"use client";

import React, { useState, useEffect, useCallback } from "react";
import { Incident, Severity, IncidentStatus } from "@/types";
import { api } from "@/lib/apiClient";
import { Badge, getSeverityVariant, getStatusVariant } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  AlertOctagon,
  Search,
  Filter,
  RefreshCw,
  ExternalLink,
  Layers,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSeverity, setSelectedSeverity] = useState<string>("ALL");
  const [selectedStatus, setSelectedStatus] = useState<string>("ALL");

  const fetchIncidents = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const params: Record<string, string> = {};
      if (selectedSeverity !== "ALL") params.severity = selectedSeverity;
      if (selectedStatus !== "ALL") params.status = selectedStatus;

      const data = await api.getItems<Incident>("/api/v1/incidents", params);
      setIncidents(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, [selectedSeverity, selectedStatus]);

  useEffect(() => {
    fetchIncidents();
  }, [fetchIncidents]);

  const filtered = incidents.filter((inc) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      inc.title?.toLowerCase().includes(q) ||
      inc.primaryService?.toLowerCase().includes(q) ||
      inc.summary?.toLowerCase().includes(q) ||
      inc.id?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-5 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <AlertOctagon className="w-5 h-5 text-rose-400" />
            Incidents Directory
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Filter, search, and navigate correlated production incidents across all services.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchIncidents}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Feed
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Incidents"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchIncidents}
        />
      )}

      {/* Filter Controls Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2 flex-1 min-w-[240px]">
          <Search className="w-3.5 h-3.5 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Filter by title, service, summary, or ID..."
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />
        </div>

        {/* Severity Filter */}
        <div className="flex items-center gap-1">
          <span className="text-slate-400 font-mono text-[11px] mr-1">SEV:</span>
          {["ALL", "SEV1", "SEV2", "SEV3", "SEV4"].map((sev) => (
            <button
              key={sev}
              onClick={() => setSelectedSeverity(sev)}
              className={clsx(
                "px-2 py-1 rounded text-[11px] font-mono transition",
                selectedSeverity === sev
                  ? "bg-indigo-600 text-white font-bold"
                  : "bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800"
              )}
            >
              {sev}
            </button>
          ))}
        </div>

        {/* Status Filter */}
        <div className="flex items-center gap-1">
          <span className="text-slate-400 font-mono text-[11px] mr-1">STATUS:</span>
          {["ALL", "DETECTED", "INVESTIGATING", "MITIGATING", "RESOLVED"].map((st) => (
            <button
              key={st}
              onClick={() => setSelectedStatus(st)}
              className={clsx(
                "px-2 py-1 rounded text-[11px] font-mono transition",
                selectedStatus === st
                  ? "bg-indigo-600 text-white font-bold"
                  : "bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800"
              )}
            >
              {st}
            </button>
          ))}
        </div>
      </div>

      {/* Incidents Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 font-mono text-[11px] text-slate-400">
                <th className="py-3 px-4">SEVERITY</th>
                <th className="py-3 px-4">INCIDENT DETAILS</th>
                <th className="py-3 px-4">PRIMARY SERVICE</th>
                <th className="py-3 px-4">AFFECTED</th>
                <th className="py-3 px-4">STATUS</th>
                <th className="py-3 px-4">DETECTED AT</th>
                <th className="py-3 px-4 text-right">ACTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {isLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={7} className="py-3 px-4">
                      <Skeleton className="h-5 w-full" />
                    </td>
                  </tr>
                ))
              ) : filtered.length > 0 ? (
                filtered.map((inc) => (
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

                    <td className="py-3 px-4">
                      <span className="font-mono text-xs text-indigo-300 bg-indigo-950/40 px-2 py-0.5 rounded border border-indigo-900/50">
                        {inc.primaryService}
                      </span>
                    </td>

                    <td className="py-3 px-4 font-mono text-slate-400">
                      {inc.affectedServices && inc.affectedServices.length > 0 ? (
                        <span className="flex items-center gap-1 text-[11px] text-amber-300">
                          <Layers className="w-3 h-3" />
                          {inc.affectedServices.length} svcs
                        </span>
                      ) : (
                        <span className="text-slate-500">—</span>
                      )}
                    </td>

                    <td className="py-3 px-4">
                      <Badge variant={getStatusVariant(inc.status)}>{inc.status}</Badge>
                    </td>

                    <td className="py-3 px-4 font-mono text-slate-400 text-[11px]">
                      {new Date(inc.createdAt).toLocaleString()}
                    </td>

                    <td className="py-3 px-4 text-right">
                      <Link href={`/incidents/${inc.id}`}>
                        <Button variant="secondary" size="xs" rightIcon={<ExternalLink className="w-3 h-3" />}>
                          Open RCA
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500 text-xs italic">
                    No incidents match the specified search or filter criteria.
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
