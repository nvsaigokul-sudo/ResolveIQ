"use client";

import React, { useState, useEffect } from "react";
import { AuditLog } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  ShieldCheck,
  Search,
  RefreshCw,
  Lock,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import clsx from "clsx";

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const fetchLogs = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getItems<AuditLog>("/api/v1/audit-logs", {
        query: searchQuery.trim(),
      });
      setLogs(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  const filtered = logs.filter((l) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      l.action?.toLowerCase().includes(q) ||
      l.actor?.toLowerCase().includes(q) ||
      l.resourceType?.toLowerCase().includes(q) ||
      l.resourceId?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-indigo-400" />
            Audit Logs Viewer
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Immutable tenant-scoped audit trail of human verifications, role changes, and operational mutations.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchLogs}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Feed
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Audit Logs"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchLogs}
        />
      )}

      {/* Query Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex items-center gap-3 text-xs">
        <Search className="w-4 h-4 text-slate-500 flex-shrink-0" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Filter audit events by action, actor, or resource ID..."
          className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1 text-xs text-slate-200 placeholder-slate-500 font-mono focus:outline-none focus:border-indigo-500"
        />
      </div>

      {/* Audit Log Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse font-mono">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 text-[11px] text-slate-400">
                <th className="py-3 px-4">TIMESTAMP</th>
                <th className="py-3 px-4">ACTION</th>
                <th className="py-3 px-4">ACTOR</th>
                <th className="py-3 px-4">RESOURCE</th>
                <th className="py-3 px-4">IP ADDRESS</th>
                <th className="py-3 px-4 text-right">DETAILS</th>
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
              ) : filtered.length > 0 ? (
                filtered.map((log) => {
                  const isExpanded = expandedId === log.id;

                  return (
                    <React.Fragment key={log.id}>
                      <tr className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-4 text-slate-400 text-[11px]">
                          {new Date(log.timestamp).toLocaleString()}
                        </td>

                        <td className="py-3 px-4">
                          <span className="text-indigo-400 font-bold bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-900/60">
                            {log.action}
                          </span>
                        </td>

                        <td className="py-3 px-4 text-slate-200">
                          {log.actor}
                        </td>

                        <td className="py-3 px-4 text-slate-300">
                          <span className="text-slate-500">[{log.resourceType}]</span> {log.resourceId}
                        </td>

                        <td className="py-3 px-4 text-slate-400 text-[11px]">
                          {log.ipAddress || "127.0.0.1"}
                        </td>

                        <td className="py-3 px-4 text-right">
                          {log.details && (
                            <button
                              onClick={() => setExpandedId(isExpanded ? null : log.id)}
                              className="text-slate-400 hover:text-slate-200 p-1"
                            >
                              {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                            </button>
                          )}
                        </td>
                      </tr>

                      {isExpanded && log.details && (
                        <tr className="bg-slate-950/80">
                          <td colSpan={6} className="p-3 text-[11px] font-mono text-slate-300">
                            <pre className="bg-slate-900 p-2.5 rounded border border-slate-800 overflow-x-auto">
                              {log.details}
                            </pre>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500 text-xs italic font-sans">
                    No audit records recorded yet.
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
