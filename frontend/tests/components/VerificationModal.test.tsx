import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { VerificationModal } from "@/components/incidents/VerificationModal";
import { RootCauseCandidate } from "@/types";
import React from "react";

describe("VerificationModal Component", () => {
  const dummyCandidate: RootCauseCandidate = {
    id: "cand-101",
    tenantId: "tenant-demo",
    incidentId: "inc-101",
    serviceName: "order-service",
    hypothesis: "HikariCP connection pool exhausted by unindexed SQL query",
    confidenceScore: 0.89,
    reasoning: "Deadlocks observed in DB telemetry coincident with latency spike",
    supportingEvidenceIds: ["ev-1", "ev-2"],
    counterEvidenceIds: [],
    recommendedActions: ["Restart pod pool", "Index query"],
    verificationStatus: "PENDING",
    createdAt: new Date().toISOString(),
  };

  it("renders candidate hypothesis and target service", () => {
    render(
      <VerificationModal
        isOpen={true}
        onClose={vi.fn()}
        incidentId="inc-101"
        candidate={dummyCandidate}
        onVerified={vi.fn()}
      />
    );

    expect(screen.getByText("order-service")).toBeInTheDocument();
    expect(
      screen.getByText("HikariCP connection pool exhausted by unindexed SQL query")
    ).toBeInTheDocument();
    expect(screen.getByText("89%")).toBeInTheDocument();
  });

  it("does not render when isOpen is false", () => {
    const { container } = render(
      <VerificationModal
        isOpen={false}
        onClose={vi.fn()}
        incidentId="inc-101"
        candidate={dummyCandidate}
        onVerified={vi.fn()}
      />
    );

    expect(container.firstChild).toBeNull();
  });

  it("requires rationale when rejecting hypothesis", async () => {
    render(
      <VerificationModal
        isOpen={true}
        onClose={vi.fn()}
        incidentId="inc-101"
        candidate={dummyCandidate}
        initialDecision="REJECTED"
        onVerified={vi.fn()}
      />
    );

    const submitBtn = screen.getByText("Submit Decision");
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText(/Rationale \/ feedback notes are required/)
    ).toBeInTheDocument();
  });
});
