"use client";

import React, { useState } from "react";
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
} from "lucide-react";

export default function LoginPage() {
  const router = useRouter();
  const { login, availableTenants, isLoading } = useAuth();

  const [email, setEmail] = useState("sre@resolveiq.io");
  const [tenantId, setTenantId] = useState("");
  const [role, setRole] = useState("SRE");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      await login(email.trim(), role, tenantId || (availableTenants[0]?.id));
      router.push("/");
    } catch (err: any) {
      setError(err.message || "Authentication failed. Please verify credentials.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4 selection:bg-indigo-500 selection:text-white">
      {/* Brand Logo */}
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 rounded-lg bg-gradient-to-tr from-indigo-600 to-cyan-500 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20">
          <Flame className="w-6 h-6" />
        </div>
        <div>
          <span className="font-mono font-extrabold text-2xl text-slate-100 tracking-tight">
            Resolve<span className="text-indigo-400">IQ</span>
          </span>
          <span className="text-[10px] uppercase px-1.5 py-0.5 ml-2 rounded bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">
            SRE Portal
          </span>
        </div>
      </div>

      {/* Login Card */}
      <Card className="w-full max-w-md border-slate-800 bg-slate-900/90 shadow-2xl">
        <CardBody className="p-6 space-y-5">
          <div>
            <h2 className="text-base font-semibold text-slate-100">Operator Sign In</h2>
            <p className="text-xs text-slate-400 mt-1">
              Authenticate to access tenant incidents, automated correlation, and telemetry.
            </p>
          </div>

          {error && (
            <div className="p-3 rounded bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4 text-xs">
            {/* Organization / Tenant */}
            <div>
              <label className="block text-slate-300 font-medium mb-1.5 flex items-center gap-1.5">
                <Building2 className="w-3.5 h-3.5 text-indigo-400" />
                <span>Organization Tenant</span>
              </label>
              <select
                value={tenantId}
                onChange={(e) => setTenantId(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
              >
                {availableTenants.length > 0 ? (
                  availableTenants.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name} ({t.slug})
                    </option>
                  ))
                ) : (
                  <option value="default">Default Organization</option>
                )}
              </select>
            </div>

            {/* Email */}
            <div>
              <label className="block text-slate-300 font-medium mb-1.5">
                Operator Email
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="engineer@company.io"
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
                value={role}
                onChange={(e) => setRole(e.target.value)}
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

          {/* SSO Placeholder */}
          <div className="pt-4 border-t border-slate-800">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full text-slate-400"
              leftIcon={<Lock className="w-3.5 h-3.5" />}
              onClick={() => handleSubmit({ preventDefault: () => {} } as any)}
            >
              Single Sign-On (Okta / SAML)
            </Button>
          </div>
        </CardBody>
      </Card>

      <div className="mt-6 text-[11px] font-mono text-slate-500">
        ResolveIQ Platform v1.0 • Enterprise SRE Incident Intelligence
      </div>
    </div>
  );
}
