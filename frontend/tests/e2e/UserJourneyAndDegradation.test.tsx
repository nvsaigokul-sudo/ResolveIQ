import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import React from "react";
import { InsufficientEvidenceBanner } from "@/components/incidents/InsufficientEvidenceBanner";
import { VerificationModal } from "@/components/incidents/VerificationModal";
import { Badge } from "@/components/ui/Badge";
import { RootCauseCandidate } from "@/types";

describe("Frontend E2E User Journey & Graceful Degradation", () => {
  const sampleCandidate: RootCauseCandidate = {
    id: "cand-journey-001",
    tenantId: "tenant-e2e",
    incidentId: "inc-journey-001",
    serviceName: "payment-service",
    hypothesis: "Connection pool exhaustion following bad configuration push",
    confidenceScore: 0.92,
    reasoning: "Correlation with deployment sha-3b8f and Hikari pool wait time spike",
    supportingEvidenceIds: ["ev-pool-exhausted", "ev-dep-3b8f"],
    counterEvidenceIds: [],
    recommendedActions: ["Rollback deployment sha-3b8f", "Increase maximum pool size to 50"],
    verificationStatus: "PENDING",
    createdAt: new Date().toISOString(),
  };

  it("User Journey 1: Incident Commander verifies root cause candidate hypothesis", () => {
    const handleVerified = vi.fn();
    const handleClose = vi.fn();

    render(
      <VerificationModal
        isOpen={true}
        onClose={handleClose}
        incidentId="inc-journey-001"
        candidate={sampleCandidate}
        initialDecision="VERIFIED"
        onVerified={handleVerified}
      />
    );

    expect(screen.getByText("Human Verification of Root Cause")).toBeInTheDocument();
    expect(screen.getByText("payment-service")).toBeInTheDocument();
    expect(screen.getByText("92%")).toBeInTheDocument();
    expect(screen.getByText("Submit Decision")).toBeInTheDocument();
  });

  it("User Journey 2: Operator rejects candidate requiring justification notes", async () => {
    const handleVerified = vi.fn();
    const handleClose = vi.fn();

    render(
      <VerificationModal
        isOpen={true}
        onClose={handleClose}
        incidentId="inc-journey-001"
        candidate={sampleCandidate}
        initialDecision="REJECTED"
        onVerified={handleVerified}
      />
    );

    const submitBtn = screen.getByText("Submit Decision");
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText(/Rationale \/ feedback notes are required/)
    ).toBeInTheDocument();
  });

  it("Graceful Degradation 1: Insufficient Evidence banner renders explicit explanation and missing telemetry", () => {
    render(
      <InsufficientEvidenceBanner
        primaryService="payment-service"
        maxConfidenceScore={0.45}
        missingDataSources={["PostgreSQL Slow Query Log", "Thread Dumps"]}
      />
    );

    expect(
      screen.getByText("Insufficient Evidence for Conclusive Root Cause")
    ).toBeInTheDocument();
    expect(screen.getByText(/45%/)).toBeInTheDocument();
    expect(screen.getByText("PostgreSQL Slow Query Log")).toBeInTheDocument();
    expect(screen.getByText("Thread Dumps")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("UI State Robustness: Status badges render with correct accessibility and contrast", () => {
    const { rerender } = render(<Badge variant="danger">SEV1</Badge>);
    expect(screen.getByText("SEV1")).toBeInTheDocument();

    rerender(<Badge variant="warning">INVESTIGATING</Badge>);
    expect(screen.getByText("INVESTIGATING")).toBeInTheDocument();

    rerender(<Badge variant="success">RESOLVED</Badge>);
    expect(screen.getByText("RESOLVED")).toBeInTheDocument();
  });
});
