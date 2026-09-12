"use client";

import React, { useState, useEffect } from "react";
import { Deployment, Service } from "@/types";
import { api } from "@/lib/apiClient";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  GitCommit,
  Search,
  RefreshCw,
  GitPullRequest,
  CheckCircle2,
  XCircle,
  Clock,
  ExternalLink,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";

export default function DeploymentsPage() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [selectedService, setSelectedService] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchDeployments = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const params: Record<string, string> = {};
      if (selectedService) params.service = selectedService;
      const data = await api.getItems<Deployment>("/api/v1/deployments", params);
      setDeployments(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    api.getItems<Service>("/api/v1/services").then(setServices).catch(() => {});
    fetchDeployments();
  }, [selectedService]);

  return (
    <div className="space-y-5 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <GitCommit className="w-5 h-5 text-indigo-400" />
            Deployments & Change History
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Production release history, Git commit SHAs, and correlated deployment anomaly windows.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchDeployments}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Feed
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Deployments"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchDeployments}
        />
      )}

      {/* Filter Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2">
          <span className="text-slate-400 font-mono text-[11px]">Filter Service:</span>
          <select
            value={selectedService}
            onChange={(e) => setSelectedService(e.target.value)}
            className="bg-slate-950 border border-slate-800 rounded px-2.5 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
          >
            <option value="">All Services</option>
            {services.map((s) => (
              <option key={s.id || s.name} value={s.name}>
                {s.name}
              </option>
            ))}
          </select>
        </div>

        <div className="font-mono text-xs text-slate-400">
          Total Deployments: <span className="font-bold text-slate-200">{deployments.length}</span>
        </div>
      </div>

      {/* Deployments Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 font-mono text-[11px] text-slate-400">
                <th className="py-3 px-4">STATUS</th>
                <th className="py-3 px-4">SERVICE</th>
                <th className="py-3 px-4">VERSION / COMMIT</th>
                <th className="py-3 px-4">ENVIRONMENT</th>
                <th className="py-3 px-4">AUTHOR</th>
                <th className="py-3 px-4">DEPLOYED AT</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={6} className="py-3 px-4">
                      <Skeleton className="h-5 w-full" />
                    </td>
                  </tr>
                ))
              ) : deployments.length > 0 ? (
                deployments.map((dep) => (
                  <tr key={dep.id} className="hover:bg-slate-800/40 transition">
                    <td className="py-3 px-4">
                      {dep.status === "SUCCESS" ? (
                        <span className="inline-flex items-center gap-1 text-emerald-400 text-[11px]">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          SUCCESS
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-rose-400 text-[11px]">
                          <XCircle className="w-3.5 h-3.5" />
                          FAILED
                        </span>
                      )}
                    </td>

                    <td className="py-3 px-4 font-bold text-slate-200">
                      {dep.service}
                    </td>

                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <span className="text-indigo-400">{dep.version}</span>
                        <span className="text-[10px] text-slate-500 bg-slate-950 px-1.5 py-0.2 rounded border border-slate-800">
                          {dep.commitHash?.slice(0, 7) || "unknown"}
                        </span>
                      </div>
                    </td>

                    <td className="py-3 px-4 text-slate-300">
                      <span className="px-1.5 py-0.2 rounded bg-slate-800 text-[10px] text-slate-300 border border-slate-700">
                        {dep.environment}
                      </span>
                    </td>

                    <td className="py-3 px-4 text-slate-400 font-sans">
                      {dep.author}
                    </td>

                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {new Date(dep.deployedAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500 text-xs italic font-sans">
                    No recent deployments registered for this tenant.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
