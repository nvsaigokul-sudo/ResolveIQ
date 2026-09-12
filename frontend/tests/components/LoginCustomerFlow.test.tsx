import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import LoginPage from "@/app/login/page";
import { AuthProvider } from "@/context/AuthContext";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

describe("Login Page - Customer Onboarding & OTP Access Flow", () => {
  it("renders with Customer Sign In tab active by default", () => {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    );

    expect(screen.getByText("Customer Sign In")).toBeInTheDocument();
    expect(screen.getByText("Create Account")).toBeInTheDocument();
    expect(screen.getByText("Internal SRE")).toBeInTheDocument();
    expect(screen.getByText("Passwordless Verification")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("engineer@company.com")).toBeInTheDocument();
    expect(screen.getByText("Send One-Time Code")).toBeInTheDocument();
  });

  it("switches to Create Account tab and displays registration fields", () => {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText("Create Account"));

    expect(screen.getByText("Register Organization Account")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("jane@enterprise.com")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Acme Payments Inc.")).toBeInTheDocument();
    expect(screen.getByText("Submit Registration")).toBeInTheDocument();
  });

  it("switches to Internal Operator tab and displays operator credentials form", () => {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText("Internal SRE"));

    expect(screen.getByText("Internal Operator Access")).toBeInTheDocument();
    expect(screen.getByText("Target Tenant")).toBeInTheDocument();
    expect(screen.getByText("Assigned RBAC Role")).toBeInTheDocument();
    expect(screen.getByText("Sign In to Console")).toBeInTheDocument();
  });
});
