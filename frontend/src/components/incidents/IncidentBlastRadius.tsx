"use client";

import React from "react";
import { TopologyGraph } from "@/types";
import { Network, AlertOctagon, CheckCircle2 } from "lucide-react";
import clsx from "clsx";

interface IncidentBlastRadiusProps {
  primaryService: string;
  affectedServices: string[];
  topology: TopologyGraph | null;
}

export const IncidentBlastRadius: React.FC<IncidentBlastRadiusProps> = ({
  primaryService,
  affectedServices,
  topology,
}) => {
  if (!topology || !topology.nodes || topology.nodes.length === 0) {
    return (
      <div className="p-8 text-center bg-slate-900/30 border border-slate-800 rounded text-xs text-slate-500">
        <Network className="w-6 h-6 text-slate-600 mx-auto mb-2" />
        Dependency topology data is currently unavailable for {primaryService}.
      </div>
    );
  }

  // Calculate layout for simple SVG rendering
  const nodes = topology.nodes;
  const edges = topology.edges;

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Network className="w-4 h-4 text-indigo-400" />
          <h4 className="text-xs font-semibold text-slate-200">Incident Blast Radius Topology</h4>
        </div>
        <div className="flex items-center gap-3 text-[10px] font-mono">
          <div className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-rose-500" />
            <span className="text-slate-400">Primary Root ({primaryService})</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span className="text-slate-400">Affected ({affectedServices.length})</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span className="text-slate-400">Adjacent Healthy</span>
          </div>
        </div>
      </div>

      {/* Nodes visual grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
        {nodes.map((n) => {
          const isPrimary = n.name === primaryService;
          const isAffected = affectedServices.includes(n.name) && !isPrimary;
          const isHealthy = !isPrimary && !isAffected;

          return (
            <div
              key={n.id || n.name}
              className={clsx(
                "p-3 rounded border text-xs flex flex-col justify-between transition-all",
                isPrimary
                  ? "bg-rose-950/40 border-rose-500/80 shadow-md shadow-rose-950/30 ring-1 ring-rose-500/50"
                  : isAffected
                  ? "bg-amber-950/30 border-amber-500/60"
                  : "bg-slate-950/60 border-slate-800 text-slate-400"
              )}
            >
              <div>
                <div className="flex items-center justify-between gap-1 mb-1">
                  <span className="font-mono text-[9px] uppercase tracking-wider text-slate-400">
                    {n.tier || "SERVICE"}
                  </span>
                  {isPrimary ? (
                    <AlertOctagon className="w-3.5 h-3.5 text-rose-400 animate-pulse" />
                  ) : isAffected ? (
                    <span className="w-2 h-2 rounded-full bg-amber-400" />
                  ) : (
                    <CheckCircle2 className="w-3 h-3 text-emerald-500" />
                  )}
                </div>

                <div className="font-mono font-bold text-xs truncate text-slate-200">
                  {n.name}
                </div>
              </div>

              <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] font-mono text-slate-400">
                <span>{n.health || "UP"}</span>
                {n.activeIncidentsCount > 0 && (
                  <span className="text-rose-400 font-bold">{n.activeIncidentsCount} inc</span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Edges List */}
      {edges && edges.length > 0 && (
        <div className="mt-3 pt-3 border-t border-slate-800/80">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-400 block mb-2">
            Active Dependency Invocations ({edges.length})
          </span>
          <div className="flex flex-wrap gap-2 text-[11px] font-mono">
            {edges.map((e, idx) => (
              <div
                key={idx}
                className="px-2.5 py-1 rounded bg-slate-950 border border-slate-800 flex items-center gap-1.5 text-slate-300"
              >
                <span className={e.source === primaryService ? "text-rose-400 font-bold" : "text-slate-300"}>
                  {e.source}
                </span>
                <span className="text-slate-500">→</span>
                <span className={e.target === primaryService ? "text-rose-400 font-bold" : "text-slate-300"}>
                  {e.target}
                </span>
                <span className="text-[9px] text-indigo-400 bg-indigo-950 px-1 py-0.2 rounded border border-indigo-800">
                  {e.type}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
