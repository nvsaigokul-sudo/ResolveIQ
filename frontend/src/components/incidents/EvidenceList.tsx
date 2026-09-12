"use client";

import React, { useState } from "react";
import { Evidence, EvidenceType } from "@/types";
import { Badge } from "@/components/ui/Badge";
import {
  Activity,
  AlertTriangle,
  Radio,
  GitCommit,
  Sliders,
  FileCode,
  BookOpen,
  ChevronDown,
  ChevronUp,
  ExternalLink,
} from "lucide-react";
import clsx from "clsx";

interface EvidenceListProps {
  evidence: Evidence[];
  highlightedId?: string | null;
}

const EVIDENCE_TYPES: EvidenceType[] = [
  "METRIC_ANOMALY",
  "LOG_ERROR_SPIKE",
  "TRACE_LATENCY_OUTLIER",
  "DEPLOYMENT_EVENT",
  "CONFIG_CHANGE",
  "DEPENDENCY_RELATION",
  "PAST_POSTMORTEM",
  "RUNBOOK",
];

export const EvidenceList: React.FC<EvidenceListProps> = ({ evidence, highlightedId }) => {
  const [filterType, setFilterType] = useState<string>("ALL");
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const toggleExpand = (id: string) => {
    const next = new Set(expandedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedIds(next);
  };

  const getEvidenceIcon = (type: string) => {
    switch (type) {
      case "METRIC_ANOMALY":
        return <Activity className="w-3.5 h-3.5 text-amber-400" />;
      case "LOG_ERROR_SPIKE":
        return <AlertTriangle className="w-3.5 h-3.5 text-rose-400" />;
      case "TRACE_LATENCY_OUTLIER":
        return <Radio className="w-3.5 h-3.5 text-indigo-400" />;
      case "DEPLOYMENT_EVENT":
        return <GitCommit className="w-3.5 h-3.5 text-purple-400" />;
      case "CONFIG_CHANGE":
        return <Sliders className="w-3.5 h-3.5 text-cyan-400" />;
      case "RUNBOOK":
        return <FileCode className="w-3.5 h-3.5 text-emerald-400" />;
      case "PAST_POSTMORTEM":
        return <BookOpen className="w-3.5 h-3.5 text-blue-400" />;
      default:
        return <Activity className="w-3.5 h-3.5 text-slate-400" />;
    }
  };

  const filtered = evidence.filter((e) => filterType === "ALL" || e.evidenceType === filterType);

  return (
    <div className="space-y-4">
      {/* Type Filter Pills */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
        <button
          onClick={() => setFilterType("ALL")}
          className={clsx(
            "px-2.5 py-1 rounded font-mono text-[11px] border transition",
            filterType === "ALL"
              ? "bg-indigo-600/20 text-indigo-300 border-indigo-500/40 font-bold"
              : "bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200"
          )}
        >
          ALL ({evidence.length})
        </button>

        {EVIDENCE_TYPES.map((t) => {
          const count = evidence.filter((e) => e.evidenceType === t).length;
          if (count === 0) return null;

          return (
            <button
              key={t}
              onClick={() => setFilterType(t)}
              className={clsx(
                "px-2.5 py-1 rounded font-mono text-[11px] border transition flex items-center gap-1.5 whitespace-nowrap",
                filterType === t
                  ? "bg-indigo-600/20 text-indigo-300 border-indigo-500/40 font-bold"
                  : "bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200"
              )}
            >
              {getEvidenceIcon(t)}
              <span>{t.replace(/_/g, " ")}</span>
              <span className="text-[10px] text-slate-500">({count})</span>
            </button>
          );
        })}
      </div>

      {/* Evidence Cards */}
      <div className="space-y-2">
        {filtered.length > 0 ? (
          filtered.map((item) => {
            const isExpanded = expandedIds.has(item.id);
            const isHighlighted = highlightedId === item.id;
            const confidencePct = Math.round(item.confidenceScore * 100);

            return (
              <div
                key={item.id}
                id={`evidence-${item.id}`}
                className={clsx(
                  "bg-slate-900/70 border rounded-md p-3 text-xs transition duration-150",
                  isHighlighted
                    ? "border-indigo-500 ring-2 ring-indigo-500/30 bg-indigo-950/20"
                    : "border-slate-800 hover:border-slate-700"
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5 min-w-0">
                    <div className="p-1.5 rounded bg-slate-800/80 border border-slate-700 flex-shrink-0 mt-0.5">
                      {getEvidenceIcon(item.evidenceType)}
                    </div>

                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-mono font-semibold text-[11px] text-indigo-300">
                          {item.evidenceType.replace(/_/g, " ")}
                        </span>
                        <span className="text-[10px] font-mono text-slate-400">
                          src: {item.source}
                        </span>
                        {item.deepLink && (
                          <a
                            href={item.deepLink}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-[10px] text-indigo-400 hover:text-indigo-300 flex items-center gap-0.5 underline font-mono"
                          >
                            <span>Open In Telemetry</span>
                            <ExternalLink className="w-2.5 h-2.5" />
                          </a>
                        )}
                      </div>

                      <p className="text-slate-200 mt-1 font-medium">{item.summary}</p>
                    </div>
                  </div>

                  {/* Confidence Badge & Expand */}
                  <div className="flex items-center gap-3 flex-shrink-0">
                    <div className="text-right font-mono">
                      <span className="text-[10px] text-slate-400 block">Weight</span>
                      <span className="text-xs font-bold text-slate-300">{confidencePct}%</span>
                    </div>

                    <button
                      type="button"
                      onClick={() => toggleExpand(item.id)}
                      className="p-1 text-slate-500 hover:text-slate-300 rounded hover:bg-slate-800"
                      title="Inspect Raw Payload"
                    >
                      {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Raw JSON inspection */}
                {isExpanded && item.payload && (
                  <div className="mt-3 pt-2.5 border-t border-slate-800">
                    <div className="text-[10px] font-mono uppercase text-slate-400 mb-1">
                      Raw Evidence Telemetry Data
                    </div>
                    <pre className="p-2.5 bg-slate-950 border border-slate-800/80 rounded text-[11px] font-mono text-slate-300 overflow-x-auto">
                      {JSON.stringify(item.payload, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            );
          })
        ) : (
          <div className="p-8 text-center text-slate-500 text-xs italic bg-slate-900/30 rounded border border-slate-800/50">
            No evidence matches the selected filter.
          </div>
        )}
      </div>
    </div>
  );
};
