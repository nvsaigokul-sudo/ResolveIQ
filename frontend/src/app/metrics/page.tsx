"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { MetricSeries, Service } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { BarChart3, Search, RefreshCw, Activity, ArrowUpRight } from "lucide-react";
import clsx from "clsx";

const DEFAULT_METRICS = [
  "http.server.duration",
  "http.server.requests.errors",
  "jvm.memory.used",
  "system.cpu.utilization",
  "kafka.consumer.lag",
];

function MetricsExplorerContent() {
  const searchParams = useSearchParams();
  const initialService = searchParams?.get("service") || "";

  const [metrics, setMetrics] = useState<MetricSeries | null>(null);
  const [services, setServices] = useState<Service[]>([]);
  const [selectedService, setSelectedService] = useState(initialService);
  const [selectedMetric, setSelectedMetric] = useState(DEFAULT_METRICS[0]);
  const [timeRange, setTimeRange] = useState("1h");

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchMetrics = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.get<MetricSeries>("/api/v1/telemetry/metrics", {
        service: selectedService,
        metricName: selectedMetric,
        timeRange,
      });
      setMetrics(data);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, [selectedService, selectedMetric, timeRange]);

  useEffect(() => {
    api.getItems<Service>("/api/v1/services").then((svcs) => {
      setServices(svcs || []);
      if (!selectedService && svcs && svcs.length > 0) {
        setSelectedService(svcs[0].name);
      }
    }).catch(() => {});
  }, [selectedService]);

  useEffect(() => {
    if (selectedService) {
      fetchMetrics();
    }
  }, [selectedService, fetchMetrics]);

  // Aggregate statistics calculation
  const values = metrics?.dataPoints?.map((dp) => dp.value) || [];
  const maxVal = values.length > 0 ? Math.max(...values) : 0;
  const minVal = values.length > 0 ? Math.min(...values) : 0;
  const avgVal = values.length > 0 ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : 0;

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-indigo-400" />
            Metrics Explorer
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Query time-series telemetry, detect anomaly anomalies, and inspect operational metrics.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchMetrics}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Query
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Query Metrics"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchMetrics}
        />
      )}

      {/* Control Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-3 flex-wrap">
          {/* Service Selector */}
          <div className="flex items-center gap-2">
            <span className="text-slate-400 font-mono text-[11px]">Service:</span>
            <select
              value={selectedService}
              onChange={(e) => setSelectedService(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded px-2.5 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
            >
              {services.map((s) => (
                <option key={s.id || s.name} value={s.name}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>

          {/* Metric Selector */}
          <div className="flex items-center gap-2">
            <span className="text-slate-400 font-mono text-[11px]">Metric:</span>
            <select
              value={selectedMetric}
              onChange={(e) => setSelectedMetric(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded px-2.5 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
            >
              {DEFAULT_METRICS.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Time Range Filter */}
        <div className="flex items-center gap-1">
          {["15m", "1h", "6h", "24h"].map((tr) => (
            <button
              key={tr}
              onClick={() => setTimeRange(tr)}
              className={clsx(
                "px-2 py-1 rounded text-[11px] font-mono transition",
                timeRange === tr
                  ? "bg-indigo-600 text-white font-bold"
                  : "bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800"
              )}
            >
              {tr}
            </button>
          ))}
        </div>
      </div>

      {/* Aggregate Stats Cards */}
      <div className="grid grid-cols-3 gap-4">
        <Card className="border-slate-800 bg-slate-900/70">
          <CardBody className="p-3 text-center">
            <span className="text-[10px] font-mono uppercase text-slate-400">Peak (Max)</span>
            <div className="text-lg font-bold font-mono text-rose-400 mt-1">
              {maxVal} <span className="text-[10px] font-normal text-slate-500">{metrics?.unit || "ms"}</span>
            </div>
          </CardBody>
        </Card>

        <Card className="border-slate-800 bg-slate-900/70">
          <CardBody className="p-3 text-center">
            <span className="text-[10px] font-mono uppercase text-slate-400">Average</span>
            <div className="text-lg font-bold font-mono text-amber-300 mt-1">
              {avgVal} <span className="text-[10px] font-normal text-slate-500">{metrics?.unit || "ms"}</span>
            </div>
          </CardBody>
        </Card>

        <Card className="border-slate-800 bg-slate-900/70">
          <CardBody className="p-3 text-center">
            <span className="text-[10px] font-mono uppercase text-slate-400">Min</span>
            <div className="text-lg font-bold font-mono text-emerald-400 mt-1">
              {minVal} <span className="text-[10px] font-normal text-slate-500">{metrics?.unit || "ms"}</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Time-Series Chart Visualizer */}
      <Card className="border-slate-800">
        <CardHeader
          title={
            <div className="flex items-center gap-2">
              <span className="font-mono">{selectedMetric}</span>
              <span className="text-xs text-slate-400 font-sans">({selectedService})</span>
            </div>
          }
          subtitle={`Telemetry samples over the last ${timeRange}`}
        />
        <CardBody className="p-6">
          {isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : metrics?.dataPoints && metrics.dataPoints.length > 0 ? (
            <div className="space-y-4">
              {/* Dense Sparkline Bar Chart */}
              <div className="h-48 flex items-end gap-1.5 pt-6 pb-2 px-2 bg-slate-950 rounded border border-slate-800">
                {metrics.dataPoints.map((dp, idx) => {
                  const pct = maxVal > 0 ? Math.min(100, Math.round((dp.value / maxVal) * 100)) : 10;
                  const isSpike = dp.value > avgVal * 1.5;

                  return (
                    <div
                      key={idx}
                      className="flex-1 flex flex-col items-center justify-end h-full group relative"
                    >
                      {/* Tooltip */}
                      <div className="absolute -top-8 bg-slate-900 border border-slate-700 text-[10px] font-mono px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition pointer-events-none z-10 whitespace-nowrap">
                        {dp.value} {metrics.unit} ({new Date(dp.timestamp).toLocaleTimeString()})
                      </div>

                      {/* Bar */}
                      <div
                        className={clsx(
                          "w-full rounded-t transition-all duration-150",
                          isSpike ? "bg-rose-500 hover:bg-rose-400" : "bg-indigo-500/70 hover:bg-indigo-400"
                        )}
                        style={{ height: `${Math.max(4, pct)}%` }}
                      />
                    </div>
                  );
                })}
              </div>

              <div className="flex items-center justify-between text-[11px] font-mono text-slate-500">
                <span>Start: {new Date(metrics.dataPoints[0].timestamp).toLocaleTimeString()}</span>
                <span>End: {new Date(metrics.dataPoints[metrics.dataPoints.length - 1].timestamp).toLocaleTimeString()}</span>
              </div>
            </div>
          ) : (
            <div className="py-16 text-center text-slate-500 text-xs italic">
              No time-series data points returned for this query.
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

export default function MetricsExplorerPage() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <MetricsExplorerContent />
    </Suspense>
  );
}
