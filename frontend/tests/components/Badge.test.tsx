import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge, getSeverityVariant, getStatusVariant } from "@/components/ui/Badge";
import React from "react";

describe("Badge Component", () => {
  it("renders children text accurately", () => {
    render(<Badge variant="sev1">SEV1</Badge>);
    expect(screen.getByText("SEV1")).toBeInTheDocument();
  });

  it("maps severities correctly to variants", () => {
    expect(getSeverityVariant("SEV1")).toBe("sev1");
    expect(getSeverityVariant("SEV2")).toBe("sev2");
    expect(getSeverityVariant("SEV3")).toBe("sev3");
    expect(getSeverityVariant("SEV4")).toBe("sev4");
    expect(getSeverityVariant("UNKNOWN")).toBe("neutral");
  });

  it("maps statuses correctly to variants", () => {
    expect(getStatusVariant("DETECTED")).toBe("status-detected");
    expect(getStatusVariant("INVESTIGATING")).toBe("status-investigating");
    expect(getStatusVariant("MITIGATING")).toBe("status-mitigating");
    expect(getStatusVariant("RESOLVED")).toBe("status-resolved");
    expect(getStatusVariant("CLOSED")).toBe("status-closed");
  });
});
