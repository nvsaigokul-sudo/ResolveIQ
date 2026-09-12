package com.resolveiq.backend.investigation.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service managing LLM provider routing and automatic fallback (PRD §22).
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final DeterministicInvestigationClient deterministicClient;
    private final OpenAiCompatibleLlmClient openAiClient;
    private final boolean preferDeterministic;

    public LlmService(
            DeterministicInvestigationClient deterministicClient,
            OpenAiCompatibleLlmClient openAiClient,
            @Value("${resolveiq.llm.prefer-deterministic:true}") boolean preferDeterministic) {
        this.deterministicClient = deterministicClient;
        this.openAiClient = openAiClient;
        this.preferDeterministic = preferDeterministic;
    }

    public LlmResponse chat(LlmRequest request) {
        if (!preferDeterministic && openAiClient.isConfigured()) {
            try {
                return openAiClient.chat(request);
            } catch (Exception e) {
                log.warn("Remote LLM client failed: {}. Falling back to deterministic investigation client.", e.getMessage());
            }
        }
        return deterministicClient.chat(request);
    }

    public String getActiveModelIdentifier() {
        if (!preferDeterministic && openAiClient.isConfigured()) {
            return openAiClient.getModelIdentifier();
        }
        return deterministicClient.getModelIdentifier();
    }
}
