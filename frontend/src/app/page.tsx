"use client";

import React, { useState, useEffect } from "react";
import { DashboardSummary, Incident } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Badge, getSeverityVariant, getStatusVariant } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  AlertOctagon,
  CheckCircle2,
  Clock,
  Boxes,
  Activity,
  ArrowRight,
  ShieldCheck,
  Flame,
  Zap,
} from "lucide-react";
import Link from "next/link";

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchSummary = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.get<DashboardSummary>("/api/v1/dashboard/summary");
      setSummary(data);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSummary();
  }, []);

  const formatDuration = (seconds?: number) => {
    if (!seconds || seconds <= 0) return "0m";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Activity className="w-5 h-5 text-indigo-400" />
            Operations Overview
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Real-time incident detection, automated correlation, and platform health telemetry.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link href="/incidents">
            <Button variant="secondary" size="xs" rightIcon={<ArrowRight className="w-3.5 h-3.5" />}>
              View All Incidents
            </Button>
          </Link>
          <Link href="/dependencies">
            <Button variant="outline" size="xs">
              Topology View
            </Button>
          </Link>
        </div>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Dashboard Summary"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchSummary}
        />
      )}

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {/* Active Incidents */}
        <Card className="border-slate-800 bg-slate-900/90">
          <CardBody className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Active Incidents</span>
              <AlertOctagon className="w-4 h-4 text-rose-400" />
            </div>
            {isLoading ? (
              <Skeleton className="h-8 w-16 mt-2" />
            ) : (
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-slate-100">
                  {summary?.activeIncidents ?? 0}
                </span>
                <span className="text-[11px] font-mono text-slate-400">active</span>
              </div>
            )}
          </CardBody>
        </Card>

        {/* SEV1 & SEV2 Critical */}
        <Card className="border-slate-800 bg-slate-900/90">
          <CardBody className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Critical (SEV1 / SEV2)</span>
              <Flame className="w-4 h-4 text-amber-400" />
            </div>
            {isLoading ? (
              <Skeleton className="h-8 w-24 mt-2" />
            ) : (
              <div className="mt-2 flex items-center gap-3">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-rose-500" />
                  <span className="text-base font-bold font-mono text-rose-400">
                    {summary?.sev1Count ?? 0}
                  </span>
                  <span className="text-[10px] font-mono text-slate-400">SEV1</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-amber-500" />
                  <span className="text-base font-bold font-mono text-amber-400">
                    {summary?.sev2Count ?? 0}
                  </span>
                  <span className="text-[10px] font-mono text-slate-400">SEV2</span>
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        {/* MTTA */}
        <Card className="border-slate-800 bg-slate-900/90">
          <CardBody className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">MTTA (Mean Ack Time)</span>
              <Clock className="w-4 h-4 text-cyan-400" />
            </div>
            {isLoading ? (
              <Skeleton className="h-8 w-20 mt-2" />
            ) : (
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-cyan-300">
                  {formatDuration(summary?.mttaSeconds)}
                </span>
                <span className="text-[11px] font-mono text-slate-400">target &lt;5m</span>
              </div>
            )}
          </CardBody>
        </Card>

        {/* MTTR */}
        <Card className="border-slate-800 bg-slate-900/90">
          <CardBody className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">MTTR (Mean Resolve)</span>
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
            </div>
            {isLoading ? (
              <Skeleton className="h-8 w-20 mt-2" />
            ) : (
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-emerald-300">
                  {formatDuration(summary?.mttrSeconds)}
                </span>
                <span className="text-[11px] font-mono text-slate-400">target &lt;30m</span>
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Recent Incidents Table */}
      <Card className="border-slate-800">
        <CardHeader
          title="Recent Active & Resolved Incidents"
          subtitle="Showing latest correlation clusters across registered services"
          action={
            <Link href="/incidents">
              <Button variant="ghost" size="xs" rightIcon={<ArrowRight className="w-3 h-3" />}>
                All Incidents
              </Button>
            </Link>
          }
        />

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 font-mono text-[11px] text-slate-400">
                <th className="py-2.5 px-4">SEVERITY</th>
                <th className="py-2.5 px-4">INCIDENT</th>
                <th className="py-2.5 px-4">SERVICE</th>
                <th className="py-2.5 px-4">STATUS</th>
                <th className="py-2.5 px-4">DETECTED AT</th>
                <th className="py-2.5 px-4 text-right">ACTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={6} className="py-3 px-4">
                      <Skeleton className="h-5 w-full" />
                    </td>
                  </tr>
                ))
              ) : summary?.recentIncidents && summary.recentIncidents.length > 0 ? (
                summary.recentIncidents.map((inc) => (
                  <tr key={inc.id} className="hover:bg-slate-800/40 transition">
                    <td className="py-3 px-4">
                      <Badge variant={getSeverityVariant(inc.severity)}>{inc.severity}</Badge>
                    </td>
                    <td className="py-3 px-4 max-w-md">
                      <Link
                        href={`/incidents/${inc.id}`}
                        className="font-medium text-slate-100 hover:text-indigo-400 transition block truncate"
                      >
                        {inc.title}
                      </Link>
                      <span className="text-[11px] text-slate-400 block truncate mt-0.5">
                        {inc.summary}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-mono text-indigo-300">
                      {inc.primaryService}
                    </td>
                    <td className="py-3 px-4">
                      <Badge variant={getStatusVariant(inc.status)}>{inc.status}</Badge>
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-400 text-[11px]">
                      {new Date(inc.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link href={`/incidents/${inc.id}`}>
                        <Button variant="secondary" size="xs">
                          Investigate
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500 text-xs italic">
                    No active or historical incidents recorded.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
