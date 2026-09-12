import React from "react";
import { AlertTriangle, Database, Search } from "lucide-react";
import Link from "next/link";

interface InsufficientEvidenceBannerProps {
  primaryService?: string;
  missingDataSources?: string[];
  maxConfidenceScore?: number;
}

export const InsufficientEvidenceBanner: React.FC<InsufficientEvidenceBannerProps> = ({
  primaryService,
  missingDataSources = ["Distributed Traces", "Deployment Events", "OpenSearch Error Logs"],
  maxConfidenceScore = 0.45,
}) => {
  return (
    <div
      role="alert"
      className="p-4 rounded-lg bg-amber-950/30 border border-amber-500/40 text-slate-200 space-y-2"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-amber-300">
              Insufficient Evidence for Conclusive Root Cause
            </h4>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-500/20 text-amber-300 border border-amber-500/40 font-bold">
              CONFIDENCE {Math.round(maxConfidenceScore * 100)}% &lt; 70% THRESHOLD
            </span>
          </div>

          <p className="text-xs text-slate-300 mt-1">
            The AI investigation agent inspected available telemetry for{" "}
            <strong className="text-indigo-300 font-mono">{primaryService || "affected services"}</strong>,
            but telemetry signals are ambiguous or incomplete. Automated mitigation is withheld to prevent service risk.
          </p>

          <div className="mt-3 p-2.5 rounded bg-slate-950/60 border border-slate-800/80 text-xs space-y-1.5">
            <div className="font-mono text-[11px] text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
              <Database className="w-3.5 h-3.5 text-amber-400" />
              Recommended Manual Investigation Paths:
            </div>
            <ul className="list-disc pl-5 text-slate-300 space-y-0.5 text-[11px]">
              {missingDataSources.map((ds, i) => (
                <li key={i}>
                  Verify telemetry continuity in{" "}
                  <span className="font-mono text-amber-200 font-medium">{ds}</span>
                </li>
              ))}
              <li>
                Inspect upstream callers and downstream database queries in the Dependency Graph.
              </li>
            </ul>
          </div>

          <div className="mt-3 flex items-center gap-3 text-xs">
            <Link
              href="/traces"
              className="inline-flex items-center gap-1 font-mono text-indigo-400 hover:text-indigo-300 underline text-[11px]"
            >
              <Search className="w-3 h-3" />
              Search Traces
            </Link>
            <Link
              href="/logs"
              className="inline-flex items-center gap-1 font-mono text-indigo-400 hover:text-indigo-300 underline text-[11px]"
            >
              <Search className="w-3 h-3" />
              Search Logs
            </Link>
            <Link
              href="/dependencies"
              className="inline-flex items-center gap-1 font-mono text-indigo-400 hover:text-indigo-300 underline text-[11px]"
            >
              <Search className="w-3 h-3" />
              Inspect Topology
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
