package com.resolveiq.backend.investigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.investigation.dto.*;
import com.resolveiq.backend.investigation.loop.InvestigationLoopEngine;
import com.resolveiq.backend.investigation.loop.InvestigationLoopResult;
import com.resolveiq.backend.investigation.tools.DiscoveredEvidenceItem;
import com.resolveiq.backend.kafka.IncidentKafkaProducer;
import com.resolveiq.backend.repository.*;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.backend.service.EvidenceBuilderService;
import com.resolveiq.backend.service.IncidentLifecycleStateMachine;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.incident.*;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * AI Investigation Service coordinating the investigation loop, evidence grounding,
 * structured RCA persistence, and Kafka notifications (PRD §22, §23, §24, §51, §57).
 */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final IncidentRepository incidentRepository;
    private final InvestigationRepository investigationRepository;
    private final RootCauseCandidateRepository rootCauseCandidateRepository;
    private final CandidateEvidenceRepository candidateEvidenceRepository;
    private final EvidenceRepository evidenceRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final AuditLogService auditLogService;
    private final EvidenceBuilderService evidenceBuilderService;
    private final IncidentLifecycleStateMachine lifecycleStateMachine;
    private final IncidentKafkaProducer incidentKafkaProducer;
    private final InvestigationLoopEngine loopEngine;
    private final ObjectMapper objectMapper;

    public InvestigationService(
            IncidentRepository incidentRepository,
            InvestigationRepository investigationRepository,
            RootCauseCandidateRepository rootCauseCandidateRepository,
            CandidateEvidenceRepository candidateEvidenceRepository,
            EvidenceRepository evidenceRepository,
            IncidentEventRepository incidentEventRepository,
            AuditLogService auditLogService,
            EvidenceBuilderService evidenceBuilderService,
            IncidentLifecycleStateMachine lifecycleStateMachine,
            IncidentKafkaProducer incidentKafkaProducer,
            InvestigationLoopEngine loopEngine,
            ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.investigationRepository = investigationRepository;
        this.rootCauseCandidateRepository = rootCauseCandidateRepository;
        this.candidateEvidenceRepository = candidateEvidenceRepository;
        this.evidenceRepository = evidenceRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.auditLogService = auditLogService;
        this.evidenceBuilderService = evidenceBuilderService;
        this.lifecycleStateMachine = lifecycleStateMachine;
        this.incidentKafkaProducer = incidentKafkaProducer;
        this.loopEngine = loopEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvestigationResultDto investigate(UUID incidentId, String triggerReason) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        IncidentEntity incident = incidentRepository.findByIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Create or initialize Investigation entity
        InvestigationEntity investigation = new InvestigationEntity(tenantId, incidentId);
        investigation.setStatus(InvestigationStatus.RUNNING);
        investigation.setPromptVersion("v1.0");
        investigation = investigationRepository.save(investigation);

        // Record timeline event
        recordTimelineEvent(tenantId, incidentId, IncidentEventType.INVESTIGATION_STARTED,
                "SYSTEM", null,
                "AI investigation started: " + (triggerReason != null ? triggerReason : "Automated trigger on incident correlation"),
                String.format("{\"investigationId\": \"%s\"}", investigation.getId()));

        auditLogService.recordCurrentContext("AI_INVESTIGATION_STARTED", "investigation:" + investigation.getId(),
                "status:PENDING", "status:RUNNING");

        incidentKafkaProducer.publishInvestigationStarted(tenantId, incidentId, investigation.getId(), incident.getRootService());

        // Execute bounded investigation loop
        InvestigationLoopResult loopResult = loopEngine.run(incident, tenantId, triggerReason);
        StructuredRcaDto rca = loopResult.structuredRca();

        // Persist all new evidence items discovered during tool calls
        List<UUID> discoveredEvidenceIds = new ArrayList<>();
        if (loopResult.discoveredEvidence() != null) {
            for (DiscoveredEvidenceItem item : loopResult.discoveredEvidence()) {
                EvidenceEntity evidence = new EvidenceEntity(
                        tenantId,
                        incidentId,
                        item.source(),
                        item.service(),
                        item.dataPayload(),
                        true,
                        item.queryReference(),
                        item.relevanceScore(),
                        item.confidence()
                );
                evidence = evidenceRepository.save(evidence);
                discoveredEvidenceIds.add(evidence.getId());
            }
        }

        // Load all available evidence for this incident to guarantee 100% evidence grounding
        List<EvidenceEntity> allIncidentEvidence = evidenceRepository.findByIncidentIdAndTenantId(incidentId, tenantId);
        List<UUID> allEvidenceIds = new ArrayList<>();
        for (EvidenceEntity ee : allIncidentEvidence) {
            allEvidenceIds.add(ee.getId());
        }

        // Persist Root Cause Candidates and link evidence
        List<RootCauseCandidateDto> persistedCandidateDtos = new ArrayList<>();
        if (rca.candidates() != null) {
            for (RootCauseCandidateDto candidateDto : rca.candidates()) {
                RootCauseCandidateEntity candidateEntity = new RootCauseCandidateEntity(
                        tenantId,
                        incidentId,
                        investigation.getId(),
                        candidateDto.rank(),
                        candidateDto.hypothesis(),
                        candidateDto.rootService() != null ? candidateDto.rootService() : incident.getRootService(),
                        candidateDto.confidence(),
                        candidateDto.reasoning()
                );
                candidateEntity = rootCauseCandidateRepository.save(candidateEntity);

                // Collect supporting evidence IDs (use provided or attach incident evidence)
                List<UUID> supportingIds = new ArrayList<>();
                if (candidateDto.supportingEvidenceIds() != null && !candidateDto.supportingEvidenceIds().isEmpty()) {
                    supportingIds.addAll(candidateDto.supportingEvidenceIds());
                } else if (!allEvidenceIds.isEmpty()) {
                    supportingIds.addAll(allEvidenceIds);
                }

                for (UUID evId : supportingIds) {
                    CandidateEvidenceEntity link = new CandidateEvidenceEntity(
                            tenantId,
                            candidateEntity.getId(),
                            evId,
                            EvidenceRole.SUPPORTING
                    );
                    candidateEvidenceRepository.save(link);
                }

                persistedCandidateDtos.add(new RootCauseCandidateDto(
                        candidateEntity.getRank(),
                        candidateEntity.getHypothesis(),
                        candidateEntity.getRootService(),
                        candidateEntity.getConfidence(),
                        candidateEntity.getReasoning(),
                        supportingIds,
                        candidateDto.contradictingEvidenceIds() != null ? candidateDto.contradictingEvidenceIds() : Collections.emptyList()
                ));
            }
        }

        // Finalize Investigation status
        InvestigationStatus finalStatus = rca.insufficientEvidence() ?
                InvestigationStatus.INSUFFICIENT_EVIDENCE : InvestigationStatus.COMPLETED;

        investigation.setStatus(finalStatus);
        investigation.setModelIdentifier(loopResult.structuredRca() != null ? "resolveiq-ai-agent-v1" : "fallback");
        investigation.setToolCallCount(loopResult.toolCallCount());
        investigation.setTotalTokens(loopResult.totalTokens());
        investigation.setSummary(rca.summary());
        investigation.setUncertaintyStatement(rca.uncertaintyStatement());
        investigation.setCompletedAt(Instant.now());
        investigationRepository.save(investigation);

        // Update incident state machine if confidence threshold is met
        if (!rca.insufficientEvidence() && !persistedCandidateDtos.isEmpty() && persistedCandidateDtos.get(0).confidence() >= 0.75) {
            if (incident.getStatus() == IncidentStatus.DETECTED || incident.getStatus() == IncidentStatus.INVESTIGATING) {
                try {
                    incident.setStatus(IncidentStatus.IDENTIFIED);
                    incident.setUpdatedAt(Instant.now());
                    incidentRepository.save(incident);
                    recordTimelineEvent(tenantId, incidentId, IncidentEventType.STATUS_CHANGED,
                            "SYSTEM", null,
                            "Incident status advanced to IDENTIFIED based on high-confidence AI RCA (" + persistedCandidateDtos.get(0).rootService() + ").",
                            "{\"newStatus\": \"IDENTIFIED\"}");
                } catch (Exception e) {
                    log.warn("Non-fatal: could not transition incident status: {}", e.getMessage());
                }
            }
        }

        // Record completion timeline event
        recordTimelineEvent(tenantId, incidentId, IncidentEventType.INVESTIGATION_COMPLETED,
                "SYSTEM", null,
                String.format("Investigation %s completed with %d candidates. Summary: %s",
                        investigation.getId(), persistedCandidateDtos.size(), rca.summary()),
                String.format("{\"investigationId\": \"%s\", \"status\": \"%s\", \"candidateCount\": %d}",
                        investigation.getId(), finalStatus, persistedCandidateDtos.size()));

        auditLogService.recordCurrentContext("AI_INVESTIGATION_COMPLETED", "investigation:" + investigation.getId(),
                "status:RUNNING", "status:" + finalStatus);

        incidentKafkaProducer.publishInvestigationCompleted(tenantId, incidentId, investigation.getId(),
                finalStatus.name(), persistedCandidateDtos.size());

        StructuredRcaDto finalRcaDto = new StructuredRcaDto(
                incidentId,
                rca.summary(),
                rca.impact(),
                rca.timeline(),
                persistedCandidateDtos,
                rca.contributingFactors(),
                rca.recommendedActions(),
                rca.relevantRunbooks(),
                rca.uncertaintyStatement(),
                rca.insufficientEvidence()
        );

        return new InvestigationResultDto(
                investigation.getId(),
                incidentId,
                tenantId,
                finalStatus,
                investigation.getModelIdentifier(),
                investigation.getToolCallCount(),
                investigation.getTotalTokens(),
                investigation.getEstimatedCost(),
                loopResult.durationMs(),
                investigation.getCreatedAt(),
                investigation.getCompletedAt(),
                finalRcaDto
        );
    }

    @Transactional(readOnly = true)
    public Optional<InvestigationEntity> getLatestInvestigation(UUID incidentId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return investigationRepository.findFirstByIncidentIdAndTenantIdOrderByCreatedAtDesc(incidentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<RootCauseCandidateEntity> getCandidates(UUID incidentId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return rootCauseCandidateRepository.findByIncidentIdAndTenantIdOrderByRankAsc(incidentId, tenantId);
    }

    private void recordTimelineEvent(UUID tenantId, UUID incidentId, IncidentEventType eventType,
                                     String actorType, UUID actorId, String summary, String payload) {
        IncidentEventEntity event = new IncidentEventEntity(
                tenantId,
                incidentId,
                eventType,
                actorType,
                actorId,
                summary,
                payload,
                null
        );
        incidentEventRepository.save(event);
    }
}
