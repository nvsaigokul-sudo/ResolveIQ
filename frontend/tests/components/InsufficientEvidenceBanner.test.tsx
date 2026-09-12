import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { InsufficientEvidenceBanner } from "@/components/incidents/InsufficientEvidenceBanner";
import React from "react";

describe("InsufficientEvidenceBanner Component", () => {
  it("renders insufficient evidence title and confidence percentage", () => {
    render(
      <InsufficientEvidenceBanner
        primaryService="order-service"
        maxConfidenceScore={0.42}
      />
    );

    expect(
      screen.getByText("Insufficient Evidence for Conclusive Root Cause")
    ).toBeInTheDocument();
    expect(screen.getByText(/42%/)).toBeInTheDocument();
    expect(screen.getByText(/order-service/)).toBeInTheDocument();
  });

  it("renders recommended manual investigation paths", () => {
    render(
      <InsufficientEvidenceBanner
        primaryService="payment-service"
        missingDataSources={["Database Lock Telemetry", "Thread Dumps"]}
      />
    );

    expect(screen.getByText("Database Lock Telemetry")).toBeInTheDocument();
    expect(screen.getByText("Thread Dumps")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
