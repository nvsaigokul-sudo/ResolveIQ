"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Card, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import {
  Flame,
  Shield,
  Building2,
  Lock,
  ArrowRight,
  AlertCircle,
  Mail,
  KeyRound,
  UserPlus,
  CheckCircle2,
  Clock,
  RefreshCw,
  Terminal,
} from "lucide-react";

type AuthTab = "customer-login" | "customer-register" | "internal-operator";

export default function LoginPage() {
  const router = useRouter();
  const {
    login,
    requestOtp,
    verifyOtp,
    registerCustomer,
    verifyEmail,
    availableTenants,
    isLoading: isAuthLoading,
  } = useAuth();

  const [activeTab, setActiveTab] = useState<AuthTab>("customer-login");
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Customer OTP Login State
  const [otpStep, setOtpStep] = useState<"request" | "verify">("request");
  const [customerEmail, setCustomerEmail] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [cooldown, setCooldown] = useState<number>(0);
  const [devOtpHint, setDevOtpHint] = useState<string | null>(null);

  // Customer Registration State
  const [regEmail, setRegEmail] = useState("");
  const [regFullName, setRegFullName] = useState("");
  const [regCompanyName, setRegCompanyName] = useState("");
  const [regJobTitle, setRegJobTitle] = useState("");
  const [regSubmitted, setRegSubmitted] = useState(false);
  const [regVerificationToken, setRegVerificationToken] = useState<string | null>(null);
  const [emailVerified, setEmailVerified] = useState(false);

  // Internal Operator State
  const [operatorEmail, setOperatorEmail] = useState("sre@resolveiq.io");
  const [operatorTenantId, setOperatorTenantId] = useState("");
  const [operatorRole, setOperatorRole] = useState("SRE");

  // Cooldown timer countdown
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  // Handle Customer OTP Request
  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerEmail.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const res = await requestOtp(customerEmail.trim());
      setOtpStep("verify");
      setCooldown(60);
      setSuccessMessage(res.message || "A 6-digit verification code has been sent to your email.");
      if (res.devOtp) {
        setDevOtpHint(res.devOtp);
      }
    } catch (err: any) {
      const msg = err.error?.message || err.message || "Unable to send verification code.";
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Customer OTP Verification
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerEmail.trim() || !otpCode.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      await verifyOtp(customerEmail.trim(), otpCode.trim());
      router.push("/");
    } catch (err: any) {
      const msg = err.error?.message || err.message || "Invalid or expired OTP code.";
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Customer Registration Submit
  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!regEmail.trim() || !regFullName.trim() || !regCompanyName.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      const res = await registerCustomer({
        email: regEmail.trim(),
        fullName: regFullName.trim(),
        companyName: regCompanyName.trim(),
        jobTitle: regJobTitle.trim() || undefined,
      });

      setRegSubmitted(true);
      setSuccessMessage(res.message || "Registration submitted successfully.");
      if (res.verificationToken) {
        setRegVerificationToken(res.verificationToken);
      }
    } catch (err: any) {
      const msg = err.error?.message || err.message || "Registration failed.";
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Quick Email Verification (for dev / demo)
  const handleVerifyEmail = async (tokenToVerify?: string) => {
    const token = tokenToVerify || regVerificationToken;
    if (!token) return;

    setIsSubmitting(true);
    setError(null);
    try {
      const res = await verifyEmail(token);
      setEmailVerified(true);
      setSuccessMessage(
        "Email verified successfully! Your account is now pending review by a ResolveIQ administrator."
      );
    } catch (err: any) {
      const msg = err.error?.message || err.message || "Verification failed.";
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Internal Operator Login
  const handleOperatorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!operatorEmail.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      await login(
        operatorEmail.trim(),
        operatorRole,
        operatorTenantId || availableTenants[0]?.id
      );
      router.push("/");
    } catch (err: any) {
      const msg = err.error?.message || err.message || "Internal authentication failed.";
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4 selection:bg-indigo-500 selection:text-white">
      {/* Brand Header */}
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 rounded-lg bg-gradient-to-tr from-indigo-600 to-cyan-500 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20">
          <Flame className="w-6 h-6" />
        </div>
        <div>
          <span className="font-mono font-extrabold text-2xl text-slate-100 tracking-tight">
            Resolve<span className="text-indigo-400">IQ</span>
          </span>
          <span className="text-[10px] uppercase px-1.5 py-0.5 ml-2 rounded bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">
            SRE Incident Intelligence
          </span>
        </div>
      </div>

      {/* Main Authentication Card */}
      <Card className="w-full max-w-md border-slate-800 bg-slate-900/90 shadow-2xl">
        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-800 bg-slate-950/50 rounded-t-lg">
          <button
            type="button"
            onClick={() => {
              setActiveTab("customer-login");
              setError(null);
              setSuccessMessage(null);
            }}
            className={`flex-1 py-3 px-3 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
              activeTab === "customer-login"
                ? "text-indigo-400 border-b-2 border-indigo-500 bg-slate-900/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <KeyRound className="w-3.5 h-3.5" />
            <span>Customer Sign In</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab("customer-register");
              setError(null);
              setSuccessMessage(null);
            }}
            className={`flex-1 py-3 px-3 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
              activeTab === "customer-register"
                ? "text-indigo-400 border-b-2 border-indigo-500 bg-slate-900/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <UserPlus className="w-3.5 h-3.5" />
            <span>Create Account</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab("internal-operator");
              setError(null);
              setSuccessMessage(null);
            }}
            className={`flex-1 py-3 px-3 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
              activeTab === "internal-operator"
                ? "text-indigo-400 border-b-2 border-indigo-500 bg-slate-900/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Internal SRE</span>
          </button>
        </div>

        <CardBody className="p-6 space-y-5">
          {/* Notifications */}
          {error && (
            <div className="p-3 rounded bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {successMessage && (
            <div className="p-3 rounded bg-emerald-950/40 border border-emerald-800 text-emerald-300 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* TAB 1: CUSTOMER PASSWORDLESS OTP LOGIN */}
          {activeTab === "customer-login" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
                  <KeyRound className="w-4 h-4 text-indigo-400" />
                  <span>Passwordless Verification</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Customer access is 100% passwordless via single-use 6-digit cryptographic OTPs.
                </p>
              </div>

              {otpStep === "request" ? (
                <form onSubmit={handleRequestOtp} className="space-y-4 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1.5 flex items-center gap-1.5">
                      <Mail className="w-3.5 h-3.5 text-indigo-400" />
                      <span>Corporate Email Address</span>
                    </label>
                    <input
                      type="email"
                      required
                      value={customerEmail}
                      onChange={(e) => setCustomerEmail(e.target.value)}
                      placeholder="engineer@company.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
                    />
                  </div>

                  <Button
                    type="submit"
                    variant="primary"
                    size="md"
                    isLoading={isSubmitting}
                    className="w-full"
                    rightIcon={<ArrowRight className="w-4 h-4" />}
                  >
                    Send One-Time Code
                  </Button>
                </form>
              ) : (
                <form onSubmit={handleVerifyOtp} className="space-y-4 text-xs">
                  <div>
                    <div className="flex justify-between items-center mb-1.5">
                      <label className="text-slate-300 font-medium flex items-center gap-1.5">
                        <KeyRound className="w-3.5 h-3.5 text-indigo-400" />
                        <span>6-Digit Verification Code</span>
                      </label>
                      <button
                        type="button"
                        onClick={() => {
                          setOtpStep("request");
                          setOtpCode("");
                          setDevOtpHint(null);
                        }}
                        className="text-[11px] text-indigo-400 hover:underline"
                      >
                        Change email
                      </button>
                    </div>
                    <input
                      type="text"
                      required
                      maxLength={6}
                      autoFocus
                      value={otpCode}
                      onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))}
                      placeholder="123456"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-center text-lg tracking-widest text-indigo-300 placeholder-slate-600 focus:outline-none focus:border-indigo-500 font-mono"
                    />
                    <p className="text-[11px] text-slate-400 mt-1">
                      Code sent to <span className="font-mono text-slate-300">{customerEmail}</span> (expires in 5m).
                    </p>
                  </div>

                  {devOtpHint && (
                    <div className="p-2 rounded bg-indigo-950/40 border border-indigo-800/60 text-indigo-300 text-[11px] font-mono flex items-center justify-between">
                      <span>Dev/Test OTP: <strong className="text-indigo-200 tracking-wider">{devOtpHint}</strong></span>
                      <button
                        type="button"
                        onClick={() => setOtpCode(devOtpHint)}
                        className="text-xs bg-indigo-600/40 px-2 py-0.5 rounded hover:bg-indigo-600/60 text-indigo-100"
                      >
                        Autofill
                      </button>
                    </div>
                  )}

                  <Button
                    type="submit"
                    variant="primary"
                    size="md"
                    isLoading={isSubmitting}
                    disabled={otpCode.length !== 6}
                    className="w-full"
                    rightIcon={<ArrowRight className="w-4 h-4" />}
                  >
                    Verify & Enter Dashboard
                  </Button>

                  <div className="flex items-center justify-between pt-2 text-[11px] text-slate-400 border-t border-slate-800/60">
                    <span className="flex items-center gap-1">
                      <Clock className="w-3 h-3 text-slate-500" />
                      Cooldown: {cooldown > 0 ? `${cooldown}s` : "Ready"}
                    </span>
                    <button
                      type="button"
                      disabled={cooldown > 0 || isSubmitting}
                      onClick={handleRequestOtp}
                      className="text-indigo-400 hover:underline disabled:opacity-50 disabled:no-underline flex items-center gap-1"
                    >
                      <RefreshCw className="w-3 h-3" />
                      Resend Code
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          {/* TAB 2: CREATE CUSTOMER ACCOUNT */}
          {activeTab === "customer-register" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
                  <UserPlus className="w-4 h-4 text-indigo-400" />
                  <span>Register Organization Account</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Customer onboarding requires email verification followed by administrator approval.
                </p>
              </div>

              {!regSubmitted ? (
                <form onSubmit={handleRegisterSubmit} className="space-y-3 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">Corporate Email</label>
                    <input
                      type="email"
                      required
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      placeholder="jane@enterprise.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-slate-200 focus:outline-none focus:border-indigo-500 font-mono"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-300 font-medium mb-1">Full Name</label>
                    <input
                      type="text"
                      required
                      value={regFullName}
                      onChange={(e) => setRegFullName(e.target.value)}
                      placeholder="Jane Doe"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-slate-200 focus:outline-none focus:border-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-300 font-medium mb-1">Company / Organization</label>
                    <input
                      type="text"
                      required
                      value={regCompanyName}
                      onChange={(e) => setRegCompanyName(e.target.value)}
                      placeholder="Acme Payments Inc."
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-slate-200 focus:outline-none focus:border-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-300 font-medium mb-1">Job Title (Optional)</label>
                    <input
                      type="text"
                      value={regJobTitle}
                      onChange={(e) => setRegJobTitle(e.target.value)}
                      placeholder="Staff SRE / Engineering Director"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-slate-200 focus:outline-none focus:border-indigo-500"
                    />
                  </div>

                  <Button
                    type="submit"
                    variant="primary"
                    size="md"
                    isLoading={isSubmitting}
                    className="w-full mt-2"
                    rightIcon={<ArrowRight className="w-4 h-4" />}
                  >
                    Submit Registration
                  </Button>
                </form>
              ) : (
                <div className="space-y-4 text-xs">
                  {/* Status Banner */}
                  <div
                    className={`p-3 rounded border text-xs flex flex-col gap-1.5 ${
                      emailVerified
                        ? "bg-amber-950/40 border-amber-800 text-amber-300"
                        : "bg-indigo-950/40 border-indigo-800 text-indigo-300"
                    }`}
                  >
                    <div className="flex items-center gap-2 font-medium">
                      {emailVerified ? (
                        <CheckCircle2 className="w-4 h-4 text-amber-400" />
                      ) : (
                        <Mail className="w-4 h-4 text-indigo-400" />
                      )}
                      <span>
                        Status: {emailVerified ? "PENDING_ADMIN_REVIEW" : "PENDING_EMAIL_VERIFICATION"}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-300">
                      {emailVerified
                        ? "Email verified! An internal ResolveIQ administrator is reviewing your registration to assign your organization tenant and role."
                        : `A single-use verification link has been sent to ${regEmail}. Please verify your email.`}
                    </p>
                  </div>

                  {/* Dev / Simulator Token Box */}
                  {!emailVerified && regVerificationToken && (
                    <div className="p-3 bg-slate-950 border border-slate-800 rounded space-y-2">
                      <div className="text-[11px] font-medium text-slate-400 flex items-center justify-between">
                        <span>Development / Test Verification Link:</span>
                      </div>
                      <div className="font-mono text-[10px] text-slate-400 truncate bg-slate-900 p-2 rounded border border-slate-800">
                        Token: {regVerificationToken}
                      </div>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        isLoading={isSubmitting}
                        onClick={() => handleVerifyEmail(regVerificationToken)}
                        className="w-full text-xs"
                        leftIcon={<CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />}
                      >
                        Simulate Email Verification Click
                      </Button>
                    </div>
                  )}

                  {emailVerified && (
                    <div className="p-3 bg-slate-950 border border-slate-800 rounded space-y-2 text-center">
                      <p className="text-slate-400 text-[11px]">
                        Once an administrator approves your account, you will be able to sign in using OTP.
                      </p>
                      <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={() => {
                          setCustomerEmail(regEmail);
                          setActiveTab("customer-login");
                        }}
                        className="w-full"
                      >
                        Go to Customer Sign In
                      </Button>
                    </div>
                  )}

                  <button
                    type="button"
                    onClick={() => {
                      setRegSubmitted(false);
                      setRegVerificationToken(null);
                      setEmailVerified(false);
                    }}
                    className="text-[11px] text-slate-400 hover:text-slate-200 block text-center w-full"
                  >
                    Register another account
                  </button>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: INTERNAL OPERATOR PORTAL */}
          {activeTab === "internal-operator" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
                  <Terminal className="w-4 h-4 text-indigo-400" />
                  <span>Internal Operator Access</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Direct authentication portal for internal SREs, incident commanders, and admins.
                </p>
              </div>

              <form onSubmit={handleOperatorSubmit} className="space-y-4 text-xs">
                {/* Organization / Tenant */}
                <div>
                  <label className="block text-slate-300 font-medium mb-1.5 flex items-center gap-1.5">
                    <Building2 className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Target Tenant</span>
                  </label>
                  <select
                    value={operatorTenantId}
                    onChange={(e) => setOperatorTenantId(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
                  >
                    {availableTenants.length > 0 ? (
                      availableTenants.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name} ({t.slug})
                        </option>
                      ))
                    ) : (
                      <option value="default">Default Internal Organization</option>
                    )}
                  </select>
                </div>

                {/* Email */}
                <div>
                  <label className="block text-slate-300 font-medium mb-1.5">Operator Email</label>
                  <input
                    type="email"
                    required
                    value={operatorEmail}
                    onChange={(e) => setOperatorEmail(e.target.value)}
                    placeholder="engineer@resolveiq.io"
                    className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                </div>

                {/* Role Simulator */}
                <div>
                  <label className="block text-slate-300 font-medium mb-1.5 flex items-center gap-1.5">
                    <Shield className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Assigned RBAC Role</span>
                  </label>
                  <select
                    value={operatorRole}
                    onChange={(e) => setOperatorRole(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
                  >
                    <option value="SRE">SRE (Site Reliability Engineer)</option>
                    <option value="INCIDENT_COMMANDER">INCIDENT_COMMANDER</option>
                    <option value="ADMIN">ADMIN</option>
                    <option value="OWNER">OWNER</option>
                    <option value="VIEWER">VIEWER (Read-Only)</option>
                  </select>
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  isLoading={isSubmitting}
                  className="w-full mt-2"
                  rightIcon={<ArrowRight className="w-4 h-4" />}
                >
                  Sign In to Console
                </Button>
              </form>

              {/* SSO Option */}
              <div className="pt-3 border-t border-slate-800">
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  className="w-full text-slate-400 text-xs"
                  leftIcon={<Lock className="w-3.5 h-3.5" />}
                  onClick={() => handleOperatorSubmit({ preventDefault: () => {} } as any)}
                >
                  Single Sign-On (Okta / SAML)
                </Button>
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      <div className="mt-6 text-[11px] font-mono text-slate-500">
        ResolveIQ Platform • Multi-Tenant Enterprise Security
      </div>
    </div>
  );
}
