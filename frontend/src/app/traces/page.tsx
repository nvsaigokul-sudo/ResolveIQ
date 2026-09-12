"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { TraceDetail, TraceSpan } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Radio,
  Search,
  AlertTriangle,
  Clock,
  ExternalLink,
  ChevronRight,
  Sparkles,
} from "lucide-react";
import clsx from "clsx";

function TracesExplorerContent() {
  const searchParams = useSearchParams();
  const initialTraceId = searchParams?.get("traceId") || "trace-demo-checkout-001";

  const [traceIdInput, setTraceIdInput] = useState(initialTraceId);
  const [trace, setTrace] = useState<TraceDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchTrace = useCallback(async (tid: string) => {
    if (!tid) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.get<TraceDetail>(`/api/v1/telemetry/traces/${tid}`);
      setTrace(data);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTrace(initialTraceId);
  }, [initialTraceId, fetchTrace]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (traceIdInput.trim()) {
      fetchTrace(traceIdInput.trim());
    }
  };

  const totalDurationUs = trace ? trace.totalDurationMs * 1000 : 1;

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Radio className="w-5 h-5 text-indigo-400" />
            Traces Waterfall Explorer
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            End-to-end distributed trace latency, cross-service RPC timelines, and exception propagation.
          </p>
        </div>
      </div>

      {/* Query Bar */}
      <form
        onSubmit={handleSearch}
        className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex items-center gap-3 text-xs"
      >
        <div className="flex items-center gap-2 flex-1">
          <Search className="w-4 h-4 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            value={traceIdInput}
            onChange={(e) => setTraceIdInput(e.target.value)}
            placeholder="Enter distributed trace ID (e.g. 'trace-demo-checkout-001')..."
            className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 font-mono focus:outline-none focus:border-indigo-500"
          />
        </div>

        <Button type="submit" variant="primary" size="xs" isLoading={isLoading}>
          Load Waterfall
        </Button>
      </form>

      {error && (
        <ErrorAlert
          title="Failed to Load Trace"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={() => fetchTrace(traceIdInput)}
        />
      )}

      {/* Trace Overview Summary */}
      {trace && (
        <div className="bg-slate-900 border border-slate-800 rounded-lg p-4 flex flex-wrap items-center justify-between gap-4 text-xs font-mono">
          <div className="flex items-center gap-3">
            <span className="text-slate-400">Trace:</span>
            <span className="font-bold text-indigo-300">{trace.traceId}</span>
            <span className="text-slate-500">|</span>
            <span className="text-slate-400">Root:</span>
            <span className="text-slate-200 font-bold">{trace.rootService}</span>
          </div>

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-slate-400" />
              <span className="text-slate-400">Duration:</span>
              <span className="font-bold text-slate-100">{trace.totalDurationMs} ms</span>
            </div>

            {trace.hasError ? (
              <span className="px-2 py-0.5 rounded bg-rose-950/60 text-rose-400 border border-rose-800 font-bold flex items-center gap-1">
                <AlertTriangle className="w-3 h-3" />
                ERROR DETECTED
              </span>
            ) : (
              <span className="px-2 py-0.5 rounded bg-emerald-950/60 text-emerald-400 border border-emerald-800 font-bold">
                OK
              </span>
            )}
          </div>
        </div>
      )}

      {/* Waterfall Visualization */}
      <Card className="border-slate-800">
        <CardHeader
          title="Span Waterfall & Hierarchy"
          subtitle="Relative span start and execution duration"
        />
        <CardBody className="p-4">
          {isLoading ? (
            <Skeleton lines={6} />
          ) : trace?.spans && trace.spans.length > 0 ? (
            <div className="space-y-2">
              {trace.spans.map((span, idx) => {
                const isError = span.statusCode === "ERROR" || span.errorMessage;
                const offsetPct = Math.max(0, Math.min(95, (span.startTimeUs / totalDurationUs) * 100));
                const widthPct = Math.max(5, Math.min(100 - offsetPct, (span.durationUs / totalDurationUs) * 100));

                return (
                  <div
                    key={span.spanId || idx}
                    className="p-2.5 rounded bg-slate-950 border border-slate-800/80 hover:border-slate-700 transition space-y-1.5"
                  >
                    <div className="flex items-center justify-between text-xs font-mono">
                      <div className="flex items-center gap-2">
                        {span.parentSpanId && <ChevronRight className="w-3 h-3 text-slate-600" />}
                        <span className="font-bold text-indigo-400">{span.serviceName}</span>
                        <span className="text-slate-400">::</span>
                        <span className="text-slate-200">{span.operationName}</span>
                      </div>

                      <div className="flex items-center gap-3">
                        <span className="text-[11px] text-slate-400">
                          {Math.round(span.durationUs / 1000)} ms
                        </span>
                        <span
                          className={clsx(
                            "text-[10px] px-1.5 py-0.2 rounded font-bold border",
                            isError
                              ? "bg-rose-950 text-rose-300 border-rose-800"
                              : "bg-slate-900 text-slate-400 border-slate-800"
                          )}
                        >
                          {span.statusCode}
                        </span>
                      </div>
                    </div>

                    {/* Proportional Waterfall Bar */}
                    <div className="h-2 bg-slate-900 rounded overflow-hidden relative">
                      <div
                        className={clsx(
                          "h-full rounded transition-all",
                          isError ? "bg-rose-500" : "bg-indigo-500"
                        )}
                        style={{
                          marginLeft: `${offsetPct}%`,
                          width: `${widthPct}%`,
                        }}
                      />
                    </div>

                    {/* Error callout if present */}
                    {span.errorMessage && (
                      <div className="text-[11px] font-mono text-rose-400 bg-rose-950/40 p-2 rounded border border-rose-900/50 mt-1">
                        Exception: {span.errorMessage}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="py-16 text-center text-slate-500 text-xs italic">
              No trace spans found. Try searching for &quot;trace-demo-checkout-001&quot;.
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

export default function TracesExplorerPage() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <TracesExplorerContent />
    </Suspense>
  );
}
