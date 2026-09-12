"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Service, DependencyEdge, TopologyGraph } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Network,
  Search,
  Filter,
  Layers,
  ArrowRight,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
} from "lucide-react";
import clsx from "clsx";

function DependenciesContent() {
  const searchParams = useSearchParams();
  const initialFocus = searchParams?.get("focus") || "";

  const [services, setServices] = useState<Service[]>([]);
  const [dependencies, setDependencies] = useState<DependencyEdge[]>([]);
  const [selectedService, setSelectedService] = useState<string>(initialFocus);
  const [topology, setTopology] = useState<TopologyGraph | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [svcData, depData] = await Promise.all([
        api.getItems<Service>("/api/v1/services"),
        api.getItems<DependencyEdge>("/api/v1/dependencies"),
      ]);

      setServices(svcData || []);
      setDependencies(depData || []);

      const target = selectedService || (svcData && svcData.length > 0 ? svcData[0].name : "");
      if (target) {
        setSelectedService(target);
        try {
          const topo = await api.get<TopologyGraph>(`/api/v1/services/${target}/topology`);
          setTopology(topo);
        } catch {
          // If topology endpoint is empty, construct fallback from edges
        }
      }
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, [selectedService]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSelectService = async (serviceName: string) => {
    setSelectedService(serviceName);
    try {
      const topo = await api.get<TopologyGraph>(`/api/v1/services/${serviceName}/topology`);
      setTopology(topo);
    } catch (err: any) {
      console.warn("Failed to load topology for service:", err);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Network className="w-5 h-5 text-indigo-400" />
            Dependency Graph & Blast Radius
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Dynamic service topology, direct and transitive dependency edges, and failure blast radius estimation.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={loadData}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Graph
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Dependencies"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={loadData}
        />
      )}

      {/* Main Two-Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Service Selector & Blast Radius */}
        <div className="space-y-4">
          <Card className="border-slate-800">
            <CardHeader title="Focus Service" subtitle="Select a service to inspect its blast radius" />
            <CardBody className="p-3">
              {isLoading ? (
                <Skeleton lines={4} />
              ) : (
                <div className="space-y-1 max-h-60 overflow-y-auto pr-1">
                  {services.map((svc) => {
                    const isSelected = svc.name === selectedService;
                    return (
                      <button
                        key={svc.id || svc.name}
                        onClick={() => handleSelectService(svc.name)}
                        className={clsx(
                          "w-full text-left px-3 py-2 rounded text-xs font-mono flex items-center justify-between transition",
                          isSelected
                            ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/40 font-bold"
                            : "text-slate-300 hover:bg-slate-800 border border-transparent"
                        )}
                      >
                        <span className="truncate">{svc.name}</span>
                        <span className="text-[10px] text-slate-500 font-sans">{svc.tier}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </CardBody>
          </Card>

          {/* Blast Radius Card */}
          <Card className="border-slate-800">
            <CardHeader
              title={
                <span className="flex items-center gap-1.5 text-amber-300">
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                  Estimated Blast Radius
                </span>
              }
              subtitle={`Transitive downstream impact if ${selectedService} fails`}
            />
            <CardBody className="p-3">
              {topology?.blastRadius && topology.blastRadius.length > 0 ? (
                <div className="space-y-2">
                  <div className="text-[11px] text-slate-400">
                    <strong className="text-amber-400 font-mono">
                      {topology.blastRadius.length}
                    </strong>{" "}
                    downstream services depend on {selectedService}:
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {topology.blastRadius.map((svc) => (
                      <span
                        key={svc}
                        className="px-2 py-1 rounded bg-amber-950/40 border border-amber-800/40 text-amber-300 font-mono text-[11px]"
                      >
                        {svc}
                      </span>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-xs text-slate-500 italic py-2">
                  No downstream dependencies detected (leaf node).
                </div>
              )}
            </CardBody>
          </Card>
        </div>

        {/* Right Column: Interactive Topology Viewer */}
        <div className="lg:col-span-2 space-y-4">
          <Card className="border-slate-800">
            <CardHeader
              title={
                <div className="flex items-center gap-2">
                  <span>Topology Map</span>
                  <span className="font-mono text-xs text-indigo-400">({selectedService})</span>
                </div>
              }
              subtitle="Directional dependency call graph"
            />
            <CardBody className="p-4">
              {isLoading ? (
                <Skeleton className="h-64 w-full" />
              ) : (
                <div className="space-y-4">
                  {/* Nodes list */}
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                    {topology?.nodes && topology.nodes.length > 0 ? (
                      topology.nodes.map((n) => {
                        const isFocus = n.name === selectedService;
                        const inBlast = topology.blastRadius?.includes(n.name);

                        return (
                          <div
                            key={n.id || n.name}
                            className={clsx(
                              "p-3 rounded-lg border text-xs flex flex-col justify-between transition",
                              isFocus
                                ? "bg-indigo-950/50 border-indigo-500 text-indigo-200 ring-2 ring-indigo-500/20"
                                : inBlast
                                ? "bg-amber-950/30 border-amber-600/50 text-amber-300"
                                : "bg-slate-950 border-slate-800 text-slate-300"
                            )}
                          >
                            <div className="flex items-center justify-between">
                              <span className="font-mono text-[10px] text-slate-500">
                                {n.tier || "TIER_2"}
                              </span>
                              {isFocus ? (
                                <span className="text-[10px] font-mono text-indigo-400 font-bold">
                                  FOCUS
                                </span>
                              ) : inBlast ? (
                                <span className="text-[10px] font-mono text-amber-400">
                                  IMPACTED
                                </span>
                              ) : (
                                <CheckCircle2 className="w-3 h-3 text-emerald-400" />
                              )}
                            </div>
                            <span className="font-mono font-bold text-sm mt-1">{n.name}</span>
                            <span className="text-[10px] font-mono text-slate-400 mt-2">
                              Health: {n.health || "HEALTHY"}
                            </span>
                          </div>
                        );
                      })
                    ) : (
                      <div className="col-span-full py-8 text-center text-slate-500 text-xs italic">
                        Select a service to view connected topology nodes.
                      </div>
                    )}
                  </div>

                  {/* Edges List */}
                  <div className="pt-4 border-t border-slate-800">
                    <h5 className="font-mono text-[11px] uppercase tracking-wider text-slate-400 mb-2">
                      Dependency Invocations ({dependencies.length})
                    </h5>
                    <div className="max-h-56 overflow-y-auto space-y-1.5 pr-1">
                      {dependencies.map((edge) => {
                        const isRelevant =
                          edge.fromService === selectedService ||
                          edge.toService === selectedService;

                        return (
                          <div
                            key={edge.id}
                            className={clsx(
                              "p-2 rounded text-xs font-mono flex items-center justify-between border",
                              isRelevant
                                ? "bg-slate-900 border-indigo-500/40 text-slate-100"
                                : "bg-slate-950 border-slate-800/80 text-slate-400"
                            )}
                          >
                            <div className="flex items-center gap-2">
                              <span className={edge.fromService === selectedService ? "text-indigo-400 font-bold" : ""}>
                                {edge.fromService}
                              </span>
                              <span className="text-slate-500">→</span>
                              <span className={edge.toService === selectedService ? "text-indigo-400 font-bold" : ""}>
                                {edge.toService}
                              </span>
                            </div>
                            <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-indigo-300 border border-slate-700">
                              {edge.callType || "REST"}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}

export default function DependenciesPage() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <DependenciesContent />
    </Suspense>
  );
}
