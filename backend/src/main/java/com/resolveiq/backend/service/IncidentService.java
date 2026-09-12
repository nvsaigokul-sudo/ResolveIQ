package com.resolveiq.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.kafka.IncidentKafkaProducer;
import com.resolveiq.backend.repository.*;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.incident.*;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Core Incident Management Service implementing PRD §19, §20, §35.3, §51.
 *
 * Responsibilities:
 * 1. Consumes correlated incidents, creating or deduplicating into canonical incidents.
 * 2. Enforces guarded lifecycle transitions and updates immutable timelines.
 * 3. Enforces strict multi-tenant isolation and records audit logs.
 * 4. Coordinates with EvidenceBuilderService for structured evidence assembly.
 * 5. Replay-safe and idempotent processing.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final RootCauseCandidateRepository rootCauseCandidateRepository;
    private final FeedbackRepository feedbackRepository;
    private final ServiceRepository serviceRepository;
    private final ProjectRepository projectRepository;
    private final IncidentLifecycleStateMachine lifecycleStateMachine;
    private final EvidenceBuilderService evidenceBuilderService;
    private final AuditLogService auditLogService;
    private final IncidentKafkaProducer incidentKafkaProducer;
    private final EvidenceSanitizer evidenceSanitizer;
    private final ObjectMapper objectMapper;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentEventRepository incidentEventRepository,
                           RootCauseCandidateRepository rootCauseCandidateRepository,
                           FeedbackRepository feedbackRepository,
                           ServiceRepository serviceRepository,
                           ProjectRepository projectRepository,
                           IncidentLifecycleStateMachine lifecycleStateMachine,
                           EvidenceBuilderService evidenceBuilderService,
                           AuditLogService auditLogService,
                           IncidentKafkaProducer incidentKafkaProducer,
                           EvidenceSanitizer evidenceSanitizer,
                           ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.rootCauseCandidateRepository = rootCauseCandidateRepository;
        this.feedbackRepository = feedbackRepository;
        this.serviceRepository = serviceRepository;
        this.projectRepository = projectRepository;
        this.lifecycleStateMachine = lifecycleStateMachine;
        this.evidenceBuilderService = evidenceBuilderService;
        this.auditLogService = auditLogService;
        this.incidentKafkaProducer = incidentKafkaProducer;
        this.evidenceSanitizer = evidenceSanitizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IncidentEntity createOrUpdateFromCorrelatedIncident(CorrelatedIncidentPayload payload) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        String fingerprint = payload.incidentFingerprint();
        String rootService = payload.rootServiceCandidate() != null ? payload.rootServiceCandidate() : "unknown-service";

        // Derive affected services from root service and blast radius
        Set<String> affectedServices = new LinkedHashSet<>();
        affectedServices.add(rootService);
        if (payload.blastRadius() != null && payload.blastRadius().impactedServices() != null) {
            affectedServices.addAll(payload.blastRadius().impactedServices());
        }

        // 1. Deduplication / Replay Check (PRD §19.3): Find active open incident with identical fingerprint
        Optional<IncidentEntity> existingOpt = incidentRepository.findByFingerprintAndTenantIdAndStatusNotIn(
                fingerprint,
                tenantId,
                List.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED)
        );

        if (existingOpt.isPresent()) {
            IncidentEntity existing = existingOpt.get();
            log.info("Deduplicating into existing open incident id={}, fingerprint={}", existing.getId(), fingerprint);

            // A. Check for severity escalation
            IncidentSeverity payloadSev = mapSeverity(payload.severity());
            if (payloadSev.isHigherThan(existing.getSeverity())) {
                IncidentSeverity oldSev = existing.getSeverity();
                existing.setSeverity(payloadSev);
                recordTimelineEvent(tenantId, existing.getId(), IncidentEventType.SEVERITY_CHANGED,
                        "SYSTEM", null,
                        String.format("Severity escalated from %s to %s due to higher severity breach.", oldSev, payloadSev),
                        String.format("{\"oldSeverity\": \"%s\", \"newSeverity\": \"%s\"}", oldSev, payloadSev),
                        null);
            }

            // B. Merge affected services
            mergeAffectedServices(existing, new ArrayList<>(affectedServices));
            existing.setUpdatedAt(Instant.now());
            incidentRepository.save(existing);

            // C. Record correlation update on timeline
            int anomalyCount = 1 + (payload.correlatedAnomalyIds() != null ? payload.correlatedAnomalyIds().size() : 0);
            recordTimelineEvent(tenantId, existing.getId(), IncidentEventType.CORRELATION_DISCOVERED,
                    "SYSTEM", null,
                    String.format("Correlation cluster updated with %d correlated anomalies across %s.",
                            anomalyCount, affectedServices),
                    serializePayloadSafely(payload),
                    null);

            // D. Build/append evidence
            evidenceBuilderService.buildEvidenceFromCorrelatedIncident(existing, payload);

            // E. Publish IncidentUpdated Kafka event
            publishIncidentEvent(existing, false);

            return existing;
        }

        // 2. Resolve project and owning team from service registry
        UUID projectId = null;
        String owningTeam = "Platform SRE";
        Optional<ServiceEntity> serviceOpt = serviceRepository.findByTenantIdAndName(tenantId, rootService);
        if (serviceOpt.isPresent()) {
            projectId = serviceOpt.get().getProjectId();
            owningTeam = serviceOpt.get().getOwnerTeam();
        } else {
            List<ProjectEntity> projects = projectRepository.findAllByTenantId(tenantId);
            if (!projects.isEmpty()) {
                projectId = projects.get(0).getId();
            } else {
                projectId = UUID.randomUUID(); // Fallback if no projects exist
            }
        }

        // 3. Create canonical incident in DETECTED state (PRD §19.1)
        IncidentSeverity severity = mapSeverity(payload.severity());
        String title = payload.title() != null && !payload.title().isBlank()
                ? payload.title()
                : String.format("[%s] Elevated latency and error degradation in %s", severity, rootService);

        IncidentEntity incident = new IncidentEntity(
                tenantId,
                projectId,
                fingerprint,
                title,
                rootService,
                severity,
                owningTeam
        );
        incident.setStatus(IncidentStatus.DETECTED);
        incident.setAffectedServices(serializeListSafely(new ArrayList<>(affectedServices)));
        IncidentEntity saved = incidentRepository.save(incident);

        // 4. Record initial timeline events
        int totalAnomalies = 1 + (payload.correlatedAnomalyIds() != null ? payload.correlatedAnomalyIds().size() : 0);
        recordTimelineEvent(tenantId, saved.getId(), IncidentEventType.ANOMALY_DETECTED,
                "SYSTEM", null,
                String.format("Initial anomaly detected on service %s: %d total anomalies in cluster.",
                        rootService, totalAnomalies),
                serializePayloadSafely(payload),
                null);

        recordTimelineEvent(tenantId, saved.getId(), IncidentEventType.CORRELATION_DISCOVERED,
                "SYSTEM", null,
                String.format("Multi-signal correlation established incident cluster with confidence %.2f (%s).",
                        payload.correlationScore(), payload.confidenceTier()),
                serializePayloadSafely(payload),
                null);

        // 5. Advance lifecycle: DETECTED -> INVESTIGATING
        lifecycleStateMachine.validateTransition(IncidentStatus.DETECTED, IncidentStatus.INVESTIGATING);
        saved.setStatus(IncidentStatus.INVESTIGATING);
        saved.setUpdatedAt(Instant.now());
        saved = incidentRepository.save(saved);

        recordTimelineEvent(tenantId, saved.getId(), IncidentEventType.INVESTIGATION_STARTED,
                "SYSTEM", null,
                "Automated investigation initiated. Dispatching evidence collection.",
                null,
                null);

        // 6. Build initial evidence package
        evidenceBuilderService.buildEvidenceFromCorrelatedIncident(saved, payload);

        // 7. Audit log creation
        auditLogService.record(
                tenantId,
                null,
                "SYSTEM",
                "INCIDENT_CREATED",
                "incident:" + saved.getId(),
                null,
                String.format("status:INVESTIGATING, severity:%s, root:%s", severity, saved.getRootService()),
                "127.0.0.1",
                "trace-" + saved.getId()
        );

        // 8. Publish IncidentCreated Kafka event
        publishIncidentEvent(saved, true);

        return saved;
    }

    @Transactional
    public IncidentEntity updateStatus(UUID incidentId, IncidentStatus targetStatus, String notes) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        IncidentEntity incident = getIncidentByIdAndTenant(incidentId, tenantId);

        IncidentStatus currentStatus = incident.getStatus();
        lifecycleStateMachine.validateTransition(currentStatus, targetStatus);

        incident.setStatus(targetStatus);
        incident.setUpdatedAt(Instant.now());

        if (targetStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
            incident.setResolutionNotes(notes != null ? notes : "Incident marked as resolved.");
        } else if (targetStatus == IncidentStatus.CLOSED) {
            incident.setClosedAt(Instant.now());
        } else if (currentStatus == IncidentStatus.RESOLVED || currentStatus == IncidentStatus.CLOSED) {
            // Reopening incident
            incident.setResolvedAt(null);
            incident.setClosedAt(null);
        }

        IncidentEntity updated = incidentRepository.save(incident);

        // Record immutable timeline event
        IncidentEventType eventType = lifecycleStateMachine.getTimelineEventTypeForTransition(targetStatus);
        String summary = notes != null && !notes.isBlank()
                ? notes
                : String.format("Incident lifecycle transition: %s -> %s", currentStatus, targetStatus);

        UUID actorId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(null);
        String actorType = TenantContextHolder.getContext().map(c -> c.actorType().name()).orElse("SYSTEM");

        recordTimelineEvent(tenantId, incidentId, eventType, actorType, actorId, summary,
                String.format("{\"fromStatus\": \"%s\", \"toStatus\": \"%s\"}", currentStatus, targetStatus), null);

        // Audit log
        auditLogService.recordCurrentContext("INCIDENT_STATUS_CHANGED", "incident:" + incidentId,
                "status:" + currentStatus, "status:" + targetStatus);

        // Publish IncidentUpdated Kafka event
        publishIncidentEvent(updated, false);

        return updated;
    }

    @Transactional
    public IncidentEntity updateSeverity(UUID incidentId, IncidentSeverity newSeverity, String reason) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        IncidentEntity incident = getIncidentByIdAndTenant(incidentId, tenantId);

        IncidentSeverity oldSeverity = incident.getSeverity();
        incident.setSeverity(newSeverity);
        incident.setUpdatedAt(Instant.now());

        IncidentEntity updated = incidentRepository.save(incident);

        UUID actorId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(null);
        String actorType = TenantContextHolder.getContext().map(c -> c.actorType().name()).orElse("SYSTEM");

        recordTimelineEvent(tenantId, incidentId, IncidentEventType.SEVERITY_CHANGED,
                actorType, actorId,
                String.format("Severity changed from %s to %s. Reason: %s", oldSeverity, newSeverity, reason),
                String.format("{\"oldSeverity\": \"%s\", \"newSeverity\": \"%s\", \"reason\": \"%s\"}", oldSeverity, newSeverity, reason),
                null);

        auditLogService.recordCurrentContext("INCIDENT_SEVERITY_CHANGED", "incident:" + incidentId,
                "severity:" + oldSeverity, "severity:" + newSeverity);

        publishIncidentEvent(updated, false);
        return updated;
    }

    @Transactional
    public IncidentEntity assignIncident(UUID incidentId, UUID assigneeId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        IncidentEntity incident = getIncidentByIdAndTenant(incidentId, tenantId);

        UUID oldAssignee = incident.getAssigneeId();
        incident.setAssigneeId(assigneeId);
        incident.setUpdatedAt(Instant.now());

        IncidentEntity updated = incidentRepository.save(incident);

        UUID actorId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(null);
        String actorType = TenantContextHolder.getContext().map(c -> c.actorType().name()).orElse("SYSTEM");

        recordTimelineEvent(tenantId, incidentId, IncidentEventType.ASSIGNMENT_CHANGED,
                actorType, actorId,
                String.format("Incident assigned to user %s (previously %s)", assigneeId, oldAssignee),
                String.format("{\"oldAssignee\": \"%s\", \"newAssignee\": \"%s\"}", oldAssignee, assigneeId),
                null);

        auditLogService.recordCurrentContext("INCIDENT_ASSIGNMENT_CHANGED", "incident:" + incidentId,
                "assignee:" + oldAssignee, "assignee:" + assigneeId);

        publishIncidentEvent(updated, false);
        return updated;
    }

    @Transactional
    public IncidentEventEntity addComment(UUID incidentId, String comment) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        getIncidentByIdAndTenant(incidentId, tenantId); // verify existence & tenant access

        String sanitized = evidenceSanitizer.sanitizeSnippet(comment);
        UUID actorId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(null);
        String actorType = TenantContextHolder.getContext().map(c -> c.actorType().name()).orElse("USER");

        IncidentEventEntity timelineEvent = recordTimelineEvent(
                tenantId, incidentId, IncidentEventType.HUMAN_COMMENT,
                actorType, actorId, sanitized, null, null
        );

        auditLogService.recordCurrentContext("INCIDENT_COMMENT_ADDED", "incident:" + incidentId, null, "comment_length:" + sanitized.length());
        return timelineEvent;
    }

    @Transactional
    public RootCauseCandidateEntity verifyCandidate(UUID incidentId, UUID candidateId,
                                                    VerificationStatus status, String notes, Integer rating) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        getIncidentByIdAndTenant(incidentId, tenantId); // ensure incident exists in tenant

        RootCauseCandidateEntity candidate = rootCauseCandidateRepository.findByIdAndTenantId(candidateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Root cause candidate not found: " + candidateId));

        UUID userId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(UUID.randomUUID());

        candidate.setVerificationStatus(status);
        candidate.setVerifiedBy(userId);
        candidate.setVerificationNotes(notes);
        candidate.setVerifiedAt(Instant.now());
        RootCauseCandidateEntity savedCandidate = rootCauseCandidateRepository.save(candidate);

        // Record feedback for offline AI evaluation datasets (PRD §57)
        FeedbackEntity feedback = new FeedbackEntity(
                tenantId,
                incidentId,
                candidateId,
                userId,
                status,
                notes,
                rating
        );
        feedbackRepository.save(feedback);

        // Record timeline event
        recordTimelineEvent(tenantId, incidentId, IncidentEventType.EVIDENCE_DISCOVERED,
                "USER", userId,
                String.format("Engineer verified candidate #%d (%s) as %s.", candidate.getRank(), candidate.getRootService(), status),
                String.format("{\"candidateId\": \"%s\", \"status\": \"%s\", \"notes\": \"%s\"}", candidateId, status, notes),
                null);

        auditLogService.recordCurrentContext("RCA_CANDIDATE_VERIFIED", "candidate:" + candidateId,
                "status:UNVERIFIED", "status:" + status);

        return savedCandidate;
    }

    @Transactional(readOnly = true)
    public IncidentEntity getIncident(UUID incidentId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return getIncidentByIdAndTenant(incidentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<IncidentEntity> listIncidents(IncidentStatus status, IncidentSeverity severity, String rootService) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (status != null) {
            return incidentRepository.findByTenantIdAndStatus(tenantId, status);
        }
        if (severity != null) {
            return incidentRepository.findByTenantIdAndSeverity(tenantId, severity);
        }
        if (rootService != null && !rootService.isBlank()) {
            return incidentRepository.findByTenantIdAndRootService(tenantId, rootService);
        }
        return incidentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<IncidentEventEntity> getTimeline(UUID incidentId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        getIncidentByIdAndTenant(incidentId, tenantId);
        return incidentEventRepository.findByIncidentIdAndTenantIdOrderByCreatedAtAsc(incidentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<RootCauseCandidateEntity> getCandidates(UUID incidentId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        getIncidentByIdAndTenant(incidentId, tenantId);
        return rootCauseCandidateRepository.findByIncidentIdAndTenantIdOrderByRankAsc(incidentId, tenantId);
    }

    private IncidentEntity getIncidentByIdAndTenant(UUID incidentId, UUID tenantId) {
        return incidentRepository.findByIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
    }

    private IncidentEventEntity recordTimelineEvent(UUID tenantId, UUID incidentId, IncidentEventType eventType,
                                                    String actorType, UUID actorId, String summary,
                                                    String payload, UUID referenceEventId) {
        IncidentEventEntity event = new IncidentEventEntity(
                tenantId,
                incidentId,
                eventType,
                actorType,
                actorId,
                summary,
                payload,
                referenceEventId
        );
        return incidentEventRepository.save(event);
    }

    private void publishIncidentEvent(IncidentEntity incident, boolean isCreated) {
        List<String> affected = parseListSafely(incident.getAffectedServices());
        IncidentEventPayload payload = new IncidentEventPayload(
                incident.getId(),
                incident.getTenantId(),
                incident.getProjectId(),
                incident.getFingerprint(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getSeverity(),
                incident.getPriority(),
                incident.getRootService(),
                affected,
                incident.getOwningTeam(),
                incident.getAssigneeId(),
                incident.getResolutionNotes(),
                incident.getUpdatedAt()
        );

        if (isCreated) {
            incidentKafkaProducer.publishIncidentCreated(payload);
        } else {
            incidentKafkaProducer.publishIncidentUpdated(payload);
        }
    }

    private void mergeAffectedServices(IncidentEntity incident, List<String> newServices) {
        if (newServices == null || newServices.isEmpty()) {
            return;
        }
        Set<String> current = new LinkedHashSet<>(parseListSafely(incident.getAffectedServices()));
        current.addAll(newServices);
        incident.setAffectedServices(serializeListSafely(new ArrayList<>(current)));
    }

    private IncidentSeverity mapSeverity(Severity sev) {
        if (sev == null) {
            return IncidentSeverity.SEV3;
        }
        return switch (sev) {
            case CRITICAL -> IncidentSeverity.SEV1;
            case HIGH -> IncidentSeverity.SEV2;
            case MEDIUM -> IncidentSeverity.SEV3;
            case LOW, INFO -> IncidentSeverity.SEV4;
        };
    }

    private String serializePayloadSafely(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeListSafely(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseListSafely(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
