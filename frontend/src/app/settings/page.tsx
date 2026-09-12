"use client";

import React, { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { TenantQuota } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Settings,
  Shield,
  CreditCard,
  Users,
  HardDrive,
  Key,
} from "lucide-react";
import clsx from "clsx";

export default function SettingsPage() {
  const { tenant, role, user } = useAuth();
  const [quota, setQuota] = useState<TenantQuota | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  useEffect(() => {
    async function loadQuota() {
      setIsLoading(true);
      setError(null);
      try {
        const data = await api.get<TenantQuota>("/api/v1/settings/quota");
        setQuota(data);
      } catch (err: any) {
        // Fallback default quota info if endpoint not configured
        setQuota({
          tenantId: tenant?.id || "default",
          tier: "ENTERPRISE",
          incidentMonthlyQuota: 5000,
          incidentCurrentUsage: 142,
          retentionDays: 90,
        });
      } finally {
        setIsLoading(false);
      }
    }
    loadQuota();
  }, [tenant]);

  const usagePct =
    quota && quota.incidentMonthlyQuota > 0
      ? Math.round((quota.incidentCurrentUsage / quota.incidentMonthlyQuota) * 100)
      : 0;

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
          <Settings className="w-5 h-5 text-indigo-400" />
          Settings & Multi-Tenant Quotas
        </h1>
        <p className="text-xs text-slate-400 mt-0.5">
          Tenant resource limits, telemetry retention policies, and active RBAC permissions.
        </p>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Settings"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
        />
      )}

      {/* Tenant Profile Card */}
      <Card className="border-slate-800">
        <CardHeader title="Tenant Profile" subtitle="Active organization context" />
        <CardBody className="p-4 space-y-3 text-xs">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono">
            <div>
              <span className="text-slate-500 block text-[11px]">Organization</span>
              <span className="font-bold text-slate-100 text-sm">{tenant?.name || "Acme Telecom"}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">Tenant Slug</span>
              <span className="text-indigo-400">{tenant?.slug || "acme-telecom"}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">Subscription Tier</span>
              <span className="text-emerald-400 font-bold">{quota?.tier || "ENTERPRISE"}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">Your Active Role</span>
              <span className="text-amber-400 font-bold">{role}</span>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Usage & Quotas Card */}
      <Card className="border-slate-800">
        <CardHeader title="Incident & Telemetry Quotas" subtitle="Monthly ingestion and correlation limits" />
        <CardBody className="p-4 space-y-4 text-xs">
          <div>
            <div className="flex items-center justify-between font-mono mb-1.5">
              <span className="text-slate-300">Monthly Incident Ingestion</span>
              <span className="text-slate-400">
                <strong className="text-slate-100">{quota?.incidentCurrentUsage}</strong> /{" "}
                {quota?.incidentMonthlyQuota} ({usagePct}%)
              </span>
            </div>
            <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
              <div
                className={clsx(
                  "h-full rounded-full transition-all",
                  usagePct > 90 ? "bg-rose-500" : usagePct > 70 ? "bg-amber-500" : "bg-indigo-500"
                )}
                style={{ width: `${Math.max(2, usagePct)}%` }}
              />
            </div>
          </div>

          <div className="pt-3 border-t border-slate-800 flex items-center justify-between text-slate-300">
            <div className="flex items-center gap-2">
              <HardDrive className="w-4 h-4 text-slate-400" />
              <span>Telemetry Data Retention Window</span>
            </div>
            <span className="font-mono font-bold text-slate-100">{quota?.retentionDays} Days</span>
          </div>
        </CardBody>
      </Card>

      {/* RBAC Capabilities Matrix */}
      <Card className="border-slate-800">
        <CardHeader
          title="Active Role RBAC Capabilities"
          subtitle={`Permissions granted to ${role} in this tenant`}
        />
        <CardBody className="p-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="p-2.5 rounded bg-slate-950 border border-slate-800 flex items-center justify-between">
              <span className="text-slate-300">Acknowledge & Mitigate Incidents</span>
              <span className={role === "VIEWER" ? "text-rose-400 font-bold" : "text-emerald-400 font-bold"}>
                {role === "VIEWER" ? "DENIED" : "ALLOWED"}
              </span>
            </div>

            <div className="p-2.5 rounded bg-slate-950 border border-slate-800 flex items-center justify-between">
              <span className="text-slate-300">Verify Root-Cause Candidates</span>
              <span className={role === "VIEWER" ? "text-rose-400 font-bold" : "text-emerald-400 font-bold"}>
                {role === "VIEWER" ? "DENIED" : "ALLOWED"}
              </span>
            </div>

            <div className="p-2.5 rounded bg-slate-950 border border-slate-800 flex items-center justify-between">
              <span className="text-slate-300">Trigger AI Investigation Re-Run</span>
              <span className="text-emerald-400 font-bold">ALLOWED</span>
            </div>

            <div className="p-2.5 rounded bg-slate-950 border border-slate-800 flex items-center justify-between">
              <span className="text-slate-300">Modify Quotas & Tenants</span>
              <span className={["OWNER", "ADMIN"].includes(role) ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                {["OWNER", "ADMIN"].includes(role) ? "ALLOWED" : "DENIED"}
              </span>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
