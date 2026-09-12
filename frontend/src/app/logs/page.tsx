"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { LogRecord, Service } from "@/types";
import { api } from "@/lib/apiClient";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Terminal,
  Search,
  RefreshCw,
  Copy,
  Check,
  ExternalLink,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";

function LogsExplorerContent() {
  const searchParams = useSearchParams();
  const initialService = searchParams?.get("service") || "";

  const [logs, setLogs] = useState<LogRecord[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [selectedService, setSelectedService] = useState(initialService);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedLevel, setSelectedLevel] = useState<string>("ALL");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  const fetchLogs = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const params: Record<string, string> = {};
      if (selectedService) params.service = selectedService;
      if (searchQuery.trim()) params.query = searchQuery.trim();
      if (selectedLevel !== "ALL") params.level = selectedLevel;

      const data = await api.getItems<LogRecord>("/api/v1/telemetry/logs", params);
      setLogs(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, [selectedService, searchQuery, selectedLevel]);

  useEffect(() => {
    api.getItems<Service>("/api/v1/services").then(setServices).catch(() => {});
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const handleCopy = (text: string, index: number) => {
    navigator.clipboard.writeText(text);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  const getLevelStyle = (level: string) => {
    switch (level?.toUpperCase()) {
      case "ERROR":
        return "text-rose-400 bg-rose-950/40 border-rose-800/40";
      case "WARN":
        return "text-amber-400 bg-amber-950/40 border-amber-800/40";
      case "INFO":
        return "text-cyan-400 bg-cyan-950/40 border-cyan-800/40";
      case "DEBUG":
        return "text-slate-400 bg-slate-800 border-slate-700";
      default:
        return "text-slate-300 bg-slate-800 border-slate-700";
    }
  };

  return (
    <div className="space-y-5 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Terminal className="w-5 h-5 text-indigo-400" />
            Logs Explorer
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Query indexed error spikes, structured application logs, and correlate with distributed traces.
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
          title="Failed to Load Logs"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchLogs}
        />
      )}

      {/* Query Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3 text-xs">
        {/* Service Selector */}
        <div className="flex items-center gap-2">
          <span className="text-slate-400 font-mono text-[11px]">Service:</span>
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

        {/* Search Query */}
        <div className="flex items-center gap-2 flex-1 max-w-md">
          <Search className="w-3.5 h-3.5 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && fetchLogs()}
            placeholder="Search log messages (e.g. 'OutOfMemory', 'timeout', '503')..."
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
          />
        </div>

        {/* Severity Filter */}
        <div className="flex items-center gap-1">
          {["ALL", "ERROR", "WARN", "INFO", "DEBUG"].map((lvl) => (
            <button
              key={lvl}
              onClick={() => setSelectedLevel(lvl)}
              className={clsx(
                "px-2 py-1 rounded text-[11px] font-mono transition",
                selectedLevel === lvl
                  ? "bg-indigo-600 text-white font-bold"
                  : "bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800"
              )}
            >
              {lvl}
            </button>
          ))}
        </div>
      </div>

      {/* Terminal Output */}
      <div className="bg-slate-950 border border-slate-800 rounded-lg font-mono text-xs overflow-hidden shadow-inner">
        <div className="px-4 py-2 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
          <span>CONSOLE LOG STREAM</span>
          <span>{logs.length} events returned</span>
        </div>

        <div className="p-3 space-y-1 max-h-[650px] overflow-y-auto divide-y divide-slate-900/80">
          {isLoading ? (
            <div className="p-4">
              <Skeleton lines={8} />
            </div>
          ) : logs.length > 0 ? (
            logs.map((log, idx) => (
              <div
                key={idx}
                className="py-1.5 px-2 hover:bg-slate-900/60 transition rounded flex items-start gap-3 group text-[11px]"
              >
                {/* Timestamp */}
                <span className="text-slate-500 flex-shrink-0 select-none">
                  {new Date(log.timestamp).toISOString().replace("T", " ").replace("Z", "")}
                </span>

                {/* Level Badge */}
                <span
                  className={clsx(
                    "px-1.5 py-0.2 rounded border text-[9px] font-bold flex-shrink-0 select-none",
                    getLevelStyle(log.level)
                  )}
                >
                  {log.level}
                </span>

                {/* Service */}
                <span className="text-indigo-400 font-bold flex-shrink-0 select-none">
                  [{log.service}]
                </span>

                {/* Message */}
                <div className="flex-1 text-slate-200 break-all font-mono">
                  {log.message}
                  {log.traceId && (
                    <Link
                      href={`/traces?traceId=${log.traceId}`}
                      className="ml-2 inline-flex items-center gap-0.5 text-indigo-400 hover:underline text-[10px]"
                    >
                      <span>trace:{log.traceId.slice(0, 8)}</span>
                      <ExternalLink className="w-2.5 h-2.5" />
                    </Link>
                  )}
                </div>

                {/* Copy button */}
                <button
                  onClick={() => handleCopy(log.message, idx)}
                  className="opacity-0 group-hover:opacity-100 text-slate-500 hover:text-slate-200 p-1 rounded transition"
                  title="Copy log line"
                >
                  {copiedIndex === idx ? (
                    <Check className="w-3 h-3 text-emerald-400" />
                  ) : (
                    <Copy className="w-3 h-3" />
                  )}
                </button>
              </div>
            ))
          ) : (
            <div className="py-12 text-center text-slate-500 text-xs italic">
              No log events matched your query.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function LogsExplorerPage() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <LogsExplorerContent />
    </Suspense>
  );
}
