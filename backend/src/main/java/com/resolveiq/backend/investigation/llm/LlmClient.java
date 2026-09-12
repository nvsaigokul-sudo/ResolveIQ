package com.resolveiq.backend.investigation.llm;

/**
 * Pluggable abstraction for LLM model providers (PRD §22, §23).
 */
public interface LlmClient {

    LlmResponse chat(LlmRequest request);

    String getModelIdentifier();
}
