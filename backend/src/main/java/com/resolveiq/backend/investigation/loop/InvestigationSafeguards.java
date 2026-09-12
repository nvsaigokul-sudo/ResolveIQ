package com.resolveiq.backend.investigation.loop;

public final class InvestigationSafeguards {

    public static final int MAX_STEPS = 12;
    public static final int MAX_TOOL_CALLS = 12;
    public static final long TOTAL_TIMEOUT_SECONDS = 90;
    public static final long PER_TOOL_TIMEOUT_SECONDS = 10;
    public static final int MAX_TOKEN_BUDGET = 16000;

    private InvestigationSafeguards() {
    }
}
