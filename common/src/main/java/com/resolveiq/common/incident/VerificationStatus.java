package com.resolveiq.common.incident;

/**
 * Human verification status for root-cause candidates according to PRD §19.3 and §57.
 * Engineers explicitly mark AI-generated root-cause candidates as:
 * Verified / Rejected / Needs More Evidence.
 */
public enum VerificationStatus {
    UNVERIFIED,
    VERIFIED,
    REJECTED,
    NEEDS_MORE_EVIDENCE
}
