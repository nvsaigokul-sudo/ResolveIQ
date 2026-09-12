"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  Incident,
  IncidentEvent,
  RootCauseCandidate,
  Evidence,
  TopologyGraph,
  IncidentStatus,
} from "@/types";
import { api, ApiError } from "@/lib/apiClient";
import { useAuth } from "@/context/AuthContext";
import { Badge, getSeverityVariant, getStatusVariant } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Tabs } from "@/components/ui/Tabs";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { Timeline } from "@/components/incidents/Timeline";
import { RcaCandidates } from "@/components/incidents/RcaCandidates";
import { EvidenceList } from "@/components/incidents/EvidenceList";
import { InsufficientEvidenceBanner } from "@/components/incidents/InsufficientEvidenceBanner";
import { IncidentBlastRadius } from "@/components/incidents/IncidentBlastRadius";
import {
  AlertOctagon,
  Bot,
  CheckCircle,
  Clock,
  ArrowLeft,
  Sparkles,
  ShieldCheck,
  RefreshCw,
  Server,
  Layers,
} from "lucide-react";
import Link from "next/link";

export default function IncidentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;
  const { role } = useAuth();

  const [incident, setIncident] = useState<Incident | null>(null);
  const [events, setEvents] = useState<IncidentEvent[]>([]);
  const [candidates, setCandidates] = useState<RootCauseCandidate[]>([]);
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [topology, setTopology] = useState<TopologyGraph | null>(null);

  const [activeTab, setActiveTab] = useState<string>("rca");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<ApiError | null>(null);

  const [isInvestigating, setIsInvestigating] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  // RBAC checks
  const canModifyIncident = ["SRE", "INCIDENT_COMMANDER", "ADMIN", "OWNER"].includes(role);

  const fetchIncidentData = useCallback(async () => {
    if (!id) return;
    setLoadError(null);

    try {
      const inc = await api.get<Incident>(`/api/v1/incidents/${id}`);
      setIncident(inc);

      // Load related datasets in parallel
      const [eventsRes, rcaRes, evidenceRes] = await Promise.allSettled([
        api.getItems<IncidentEvent>(`/api/v1/incidents/${id}/events`),
        api.getItems<RootCauseCandidate>(`/api/v1/incidents/${id}/rca`),
        api.getItems<Evidence>(`/api/v1/incidents/${id}/evidence`),
      ]);

      if (eventsRes.status === "fulfilled") setEvents(eventsRes.value);
      if (rcaRes.status === "fulfilled") setCandidates(rcaRes.value);
      if (evidenceRes.status === "fulfilled") setEvidence(evidenceRes.value);

      // Load topology for primary service if present
      if (inc?.primaryService) {
        try {
          const topo = await api.get<TopologyGraph>(`/api/v1/services/${inc.primaryService}/topology`);
          setTopology(topo);
        } catch {
          // Non-blocking topology error
        }
      }
    } catch (err: any) {
      setLoadError(err);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchIncidentData();
  }, [fetchIncidentData]);

  // Status transitions
  const handleUpdateStatus = async (newStatus: IncidentStatus) => {
    if (!incident || !canModifyIncident) return;
    setIsUpdatingStatus(true);
    try {
      const updated = await api.put<Incident>(`/api/v1/incidents/${id}/status`, {
        status: newStatus,
      });
      setIncident(updated);
      await fetchIncidentData();
    } catch (err: any) {
      alert(`Failed to update status: ${err.message}`);
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // AI Investigation Trigger with Graceful Fallback
  const handleTriggerInvestigation = async () => {
    setIsInvestigating(true);
    setAiError(null);
    try {
      await api.post(`/api/v1/incidents/${id}/investigate`);
      // Reload RCA and events
      await fetchIncidentData();
    } catch (err: any) {
      console.warn("AI investigation execution failed:", err);
      setAiError(
        "AI investigation service is currently unavailable or timed out. Graceful fallback active — manual evidence inspection enabled below."
      );
    } finally {
      setIsInvestigating(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <Skeleton className="w-24 h-6" />
          <Skeleton className="w-48 h-6" />
        </div>
        <Skeleton className="w-full h-32" />
        <Skeleton lines={6} />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="max-w-4xl mx-auto mt-8">
        <ErrorAlert
          title="Failed to Load Incident Details"
          message={loadError.message}
          code={loadError.code}
          traceId={loadError.traceId}
          onRetry={fetchIncidentData}
        />
        <div className="mt-4">
          <Link href="/incidents" className="text-xs text-indigo-400 hover:underline flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" /> Back to Incidents
          </Link>
        </div>
      </div>
    );
  }

  if (!incident) {
    return (
      <div className="max-w-md mx-auto text-center py-12">
        <AlertOctagon className="w-10 h-10 text-slate-500 mx-auto mb-3" />
        <h3 className="text-sm font-semibold text-slate-200">Incident Not Found</h3>
        <p className="text-xs text-slate-500 mt-1">Incident ID {id} does not exist in this tenant.</p>
        <Button variant="secondary" size="xs" onClick={() => router.push("/incidents")} className="mt-4">
          Back to Incidents
        </Button>
      </div>
    );
  }

  const maxConfidence =
    candidates.length > 0
      ? Math.max(...candidates.map((c) => c.confidenceScore || 0))
      : 0;

  const showInsufficientEvidence =
    candidates.length === 0 || maxConfidence < 0.7;

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Top Navigation Breadcrumb & Actions */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2 text-xs">
          <Link
            href="/incidents"
            className="text-slate-400 hover:text-slate-200 flex items-center gap-1 font-mono transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>INCIDENTS</span>
          </Link>
          <span className="text-slate-600">/</span>
          <span className="font-mono text-slate-300 font-semibold">{incident.id.slice(0, 8)}</span>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2">
          {canModifyIncident && incident.status === "DETECTED" && (
            <Button
              variant="secondary"
              size="xs"
              isLoading={isUpdatingStatus}
              onClick={() => handleUpdateStatus("INVESTIGATING")}
              leftIcon={<Clock className="w-3.5 h-3.5 text-cyan-400" />}
            >
              Acknowledge
            </Button>
          )}

          {canModifyIncident &&
            ["INVESTIGATING", "IDENTIFIED"].includes(incident.status) && (
              <Button
                variant="secondary"
                size="xs"
                isLoading={isUpdatingStatus}
                onClick={() => handleUpdateStatus("MITIGATING")}
                leftIcon={<ShieldCheck className="w-3.5 h-3.5 text-amber-400" />}
              >
                Mitigate
              </Button>
            )}

          {canModifyIncident && incident.status !== "RESOLVED" && incident.status !== "CLOSED" && (
            <Button
              variant="outline"
              size="xs"
              isLoading={isUpdatingStatus}
              onClick={() => handleUpdateStatus("RESOLVED")}
              leftIcon={<CheckCircle className="w-3.5 h-3.5 text-emerald-400" />}
            >
              Mark Resolved
            </Button>
          )}

          <Button
            variant="primary"
            size="xs"
            isLoading={isInvestigating}
            onClick={handleTriggerInvestigation}
            leftIcon={<Sparkles className="w-3.5 h-3.5" />}
          >
            Re-run AI Investigation
          </Button>
        </div>
      </div>

      {/* Incident Header Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-lg p-5 shadow-sm space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 flex-wrap mb-2">
              <Badge variant={getSeverityVariant(incident.severity)} size="md">
                {incident.severity}
              </Badge>
              <Badge variant={getStatusVariant(incident.status)} size="md">
                {incident.status}
              </Badge>
              <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                Rule: {incident.correlationRule || "DEFAULT_CORRELATION"}
              </span>
            </div>

            <h1 className="text-xl font-bold text-slate-100 tracking-tight">
              {incident.title}
            </h1>
            <p className="text-xs text-slate-400 mt-1 max-w-3xl leading-relaxed">
              {incident.summary}
            </p>
          </div>

          <div className="text-right text-xs font-mono space-y-1 bg-slate-950 p-3 rounded border border-slate-800/80">
            <div className="text-slate-400">
              Created: <span className="text-slate-200">{new Date(incident.createdAt).toLocaleTimeString()}</span>
            </div>
            {incident.acknowledgedAt && (
              <div className="text-slate-400">
                Acked: <span className="text-cyan-300">{new Date(incident.acknowledgedAt).toLocaleTimeString()}</span>
              </div>
            )}
            {incident.resolvedAt && (
              <div className="text-slate-400">
                Resolved: <span className="text-emerald-300">{new Date(incident.resolvedAt).toLocaleTimeString()}</span>
              </div>
            )}
          </div>
        </div>

        {/* Affected Services Bar */}
        <div className="pt-3 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-slate-400 font-mono text-[11px] flex items-center gap-1">
              <Server className="w-3.5 h-3.5 text-indigo-400" />
              Primary Service:
            </span>
            <span className="font-mono font-bold text-indigo-300 bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-800/50">
              {incident.primaryService}
            </span>

            {incident.affectedServices && incident.affectedServices.length > 0 && (
              <>
                <span className="text-slate-500">•</span>
                <span className="text-slate-400 font-mono text-[11px] flex items-center gap-1">
                  <Layers className="w-3.5 h-3.5 text-amber-400" />
                  Affected ({incident.affectedServices.length}):
                </span>
                {incident.affectedServices.map((svc) => (
                  <span
                    key={svc}
                    className="font-mono text-[11px] text-amber-300 bg-amber-950/40 px-1.5 py-0.5 rounded border border-amber-800/40"
                  >
                    {svc}
                  </span>
                ))}
              </>
            )}
          </div>

          <div className="font-mono text-[11px] text-slate-400">
            Lead: <span className="text-slate-200">{incident.leadInvestigatorId || "ResolveIQ AI Agent"}</span>
          </div>
        </div>
      </div>

      {/* AI Unavailable Graceful Fallback Banner */}
      {aiError && (
        <ErrorAlert
          title="AI Investigation Subsystem Unavailable"
          message={aiError}
          code="AI_AGENT_TIMEOUT"
          onRetry={handleTriggerInvestigation}
        />
      )}

      {/* Insufficient Evidence Warning Banner */}
      {showInsufficientEvidence && !aiError && (
        <InsufficientEvidenceBanner
          primaryService={incident.primaryService}
          maxConfidenceScore={maxConfidence}
        />
      )}

      {/* Tab Navigation */}
      <div>
        <Tabs
          tabs={[
            {
              id: "rca",
              label: "Structured RCA Candidates",
              count: candidates.length,
              icon: <Bot className="w-3.5 h-3.5" />,
            },
            {
              id: "timeline",
              label: "Timeline & Activity",
              count: events.length,
              icon: <Clock className="w-3.5 h-3.5" />,
            },
            {
              id: "evidence",
              label: "Telemetry Evidence",
              count: evidence.length,
              icon: <Sparkles className="w-3.5 h-3.5" />,
            },
            {
              id: "blast-radius",
              label: "Topology Blast Radius",
              count: topology?.nodes?.length || 0,
              icon: <Layers className="w-3.5 h-3.5" />,
            },
          ]}
          activeTab={activeTab}
          onChange={setActiveTab}
        />

        {/* Tab Content Panes */}
        <div className="mt-4">
          {activeTab === "rca" && (
            <RcaCandidates
              incidentId={incident.id}
              candidates={candidates}
              allEvidence={evidence}
              canVerify={canModifyIncident}
              onVerificationComplete={fetchIncidentData}
              onSelectEvidence={(eid) => {
                setActiveTab("evidence");
              }}
            />
          )}

          {activeTab === "timeline" && (
            <div className="bg-slate-900 border border-slate-800 rounded-lg p-5">
              <Timeline
                incidentId={incident.id}
                events={events}
                onEventAdded={fetchIncidentData}
                canComment={true}
              />
            </div>
          )}

          {activeTab === "evidence" && (
            <EvidenceList evidence={evidence} />
          )}

          {activeTab === "blast-radius" && (
            <IncidentBlastRadius
              primaryService={incident.primaryService}
              affectedServices={incident.affectedServices || []}
              topology={topology}
            />
          )}
        </div>
      </div>
    </div>
  );
}
