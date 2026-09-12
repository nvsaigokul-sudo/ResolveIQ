"use client";

import React, { useState, useEffect } from "react";
import { Service } from "@/types";
import { api } from "@/lib/apiClient";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Boxes,
  Search,
  CheckCircle2,
  AlertTriangle,
  AlertOctagon,
  Network,
  Terminal,
  BarChart3,
  GitFork,
  ExternalLink,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";

export default function ServicesPage() {
  const [services, setServices] = useState<Service[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);
  const [searchQuery, setSearchQuery] = useState("");

  const fetchServices = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getItems<Service>("/api/v1/services");
      setServices(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchServices();
  }, []);

  const filtered = services.filter((s) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      s.name?.toLowerCase().includes(q) ||
      s.ownerTeam?.toLowerCase().includes(q) ||
      s.tier?.toLowerCase().includes(q)
    );
  });

  const getHealthBadge = (health: string) => {
    switch (health) {
      case "HEALTHY":
        return <Badge variant="healthy">● HEALTHY</Badge>;
      case "DEGRADED":
        return <Badge variant="degraded">▲ DEGRADED</Badge>;
      case "CRITICAL":
        return <Badge variant="critical">✕ CRITICAL</Badge>;
      default:
        return <Badge variant="neutral">{health}</Badge>;
    }
  };

  return (
    <div className="space-y-5 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Boxes className="w-5 h-5 text-indigo-400" />
            Services Directory
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Registered microservices, tier classification, operational ownership, and health status.
          </p>
        </div>

        <Link href="/dependencies">
          <Button variant="secondary" size="xs" leftIcon={<Network className="w-3.5 h-3.5 text-indigo-400" />}>
            Global Dependency Graph
          </Button>
        </Link>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Services"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchServices}
        />
      )}

      {/* Filter Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2 flex-1 max-w-md">
          <Search className="w-3.5 h-3.5 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search services by name, team, or tier..."
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />
        </div>

        <div className="text-slate-400 font-mono text-xs">
          Total Services: <span className="font-bold text-slate-200">{services.length}</span>
        </div>
      </div>

      {/* Services Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {isLoading ? (
          Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="p-4 rounded-lg bg-slate-900 border border-slate-800">
              <Skeleton className="h-6 w-3/4 mb-3" />
              <Skeleton lines={3} />
            </div>
          ))
        ) : filtered.length > 0 ? (
          filtered.map((svc) => (
            <div
              key={svc.id || svc.name}
              className="bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg p-4 flex flex-col justify-between transition shadow-sm"
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700 font-semibold">
                    {svc.tier || "TIER_2"}
                  </span>
                  {getHealthBadge(svc.healthStatus)}
                </div>

                <h3 className="text-base font-bold font-mono text-slate-100">{svc.name}</h3>
                <p className="text-xs text-slate-400 mt-1">
                  Team: <span className="text-slate-300 font-medium">{svc.ownerTeam || "Platform SRE"}</span>
                </p>

                {svc.repositoryUrl && (
                  <div className="mt-2 flex items-center gap-1.5 text-[11px] font-mono text-slate-400 truncate">
                    <GitFork className="w-3 h-3 text-slate-500 flex-shrink-0" />
                    <span className="truncate">{svc.repositoryUrl}</span>
                  </div>
                )}
              </div>

              <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between">
                <div className="text-xs font-mono">
                  {svc.activeIncidentsCount > 0 ? (
                    <span className="text-rose-400 font-bold flex items-center gap-1">
                      <AlertOctagon className="w-3.5 h-3.5" />
                      {svc.activeIncidentsCount} active inc
                    </span>
                  ) : (
                    <span className="text-emerald-400 flex items-center gap-1">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Healthy
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1.5">
                  <Link href={`/dependencies?focus=${svc.name}`} title="Topology">
                    <button className="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-indigo-400 transition">
                      <Network className="w-3.5 h-3.5" />
                    </button>
                  </Link>
                  <Link href={`/metrics?service=${svc.name}`} title="Metrics">
                    <button className="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-amber-400 transition">
                      <BarChart3 className="w-3.5 h-3.5" />
                    </button>
                  </Link>
                  <Link href={`/logs?service=${svc.name}`} title="Logs">
                    <button className="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-cyan-400 transition">
                      <Terminal className="w-3.5 h-3.5" />
                    </button>
                  </Link>
                </div>
              </div>
            </div>
          ))
        ) : (
          <div className="col-span-full p-12 text-center text-slate-500 text-xs italic bg-slate-900/30 rounded border border-slate-800">
            No services match the query.
          </div>
        )}
      </div>
    </div>
  );
}
