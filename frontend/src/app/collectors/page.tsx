"use client";

import React, { useState, useEffect } from "react";
import { CollectorStatus } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Server,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Activity,
} from "lucide-react";
import clsx from "clsx";

export default function CollectorsPage() {
  const [collectors, setCollectors] = useState<CollectorStatus[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchCollectors = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getItems<CollectorStatus>("/api/v1/telemetry/collectors");
      setCollectors(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCollectors();
    const interval = setInterval(fetchCollectors, 15000);
    return () => clearInterval(interval);
  }, []);

  const formatUptime = (sec: number) => {
    const hours = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    return `${hours}h ${mins}m`;
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Server className="w-5 h-5 text-indigo-400" />
            Collector Fleet Status
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            OpenTelemetry collector buffer pressure, Kafka ingestion queue depth, and health telemetry.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchCollectors}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Fleet
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Collector Fleet"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchCollectors}
        />
      )}

      {/* Collectors Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 font-mono text-[11px] text-slate-400">
                <th className="py-3 px-4">COLLECTOR ID</th>
                <th className="py-3 px-4">HOSTNAME</th>
                <th className="py-3 px-4">STATUS</th>
                <th className="py-3 px-4">BUFFER HEALTH</th>
                <th className="py-3 px-4">QUEUE DEPTH</th>
                <th className="py-3 px-4">DROPPED EVENTS</th>
                <th className="py-3 px-4">UPTIME</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono">
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={7} className="py-3 px-4">
                      <Skeleton className="h-5 w-full" />
                    </td>
                  </tr>
                ))
              ) : collectors.length > 0 ? (
                collectors.map((c) => (
                  <tr key={c.collectorId} className="hover:bg-slate-800/40 transition">
                    <td className="py-3 px-4 font-bold text-indigo-300">
                      {c.collectorId}
                    </td>

                    <td className="py-3 px-4 text-slate-200">
                      {c.hostname}
                    </td>

                    <td className="py-3 px-4">
                      {c.status === "UP" ? (
                        <span className="inline-flex items-center gap-1 text-emerald-400 text-[11px]">
                          <CheckCircle2 className="w-3.5 h-3.5" /> UP
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-amber-400 text-[11px]">
                          <AlertTriangle className="w-3.5 h-3.5" /> {c.status}
                        </span>
                      )}
                    </td>

                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                          <div
                            className={clsx(
                              "h-full rounded-full",
                              c.bufferHealth > 80
                                ? "bg-emerald-500"
                                : c.bufferHealth > 50
                                ? "bg-amber-500"
                                : "bg-rose-500"
                            )}
                            style={{ width: `${c.bufferHealth}%` }}
                          />
                        </div>
                        <span className="text-[11px] text-slate-300">{c.bufferHealth}%</span>
                      </div>
                    </td>

                    <td className="py-3 px-4 text-slate-300">
                      {c.queueDepth}
                    </td>

                    <td className="py-3 px-4 font-bold text-slate-300">
                      {c.droppedEvents > 0 ? (
                        <span className="text-rose-400">{c.droppedEvents}</span>
                      ) : (
                        <span className="text-emerald-400">0</span>
                      )}
                    </td>

                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {formatUptime(c.uptimeSeconds)}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500 text-xs italic font-sans">
                    No active OTel collectors reporting heartbeat.
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
