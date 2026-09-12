"use client";

import React, { useState } from "react";
import { RootCauseCandidate, VerificationDecision, Evidence } from "@/types";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { VerificationModal } from "./VerificationModal";
import {
  CheckCircle2,
  XCircle,
  HelpCircle,
  ChevronDown,
  ChevronUp,
  Link2,
  ArrowRight,
  ShieldAlert,
} from "lucide-react";
import clsx from "clsx";

interface RcaCandidatesProps {
  incidentId: string;
  candidates: RootCauseCandidate[];
  allEvidence: Evidence[];
  canVerify?: boolean;
  onVerificationComplete: () => void;
  onSelectEvidence?: (evidenceId: string) => void;
}

export const RcaCandidates: React.FC<RcaCandidatesProps> = ({
  incidentId,
  candidates,
  allEvidence,
  canVerify = true,
  onVerificationComplete,
  onSelectEvidence,
}) => {
  const [selectedCandidate, setSelectedCandidate] = useState<RootCauseCandidate | null>(null);
  const [modalDecision, setModalDecision] = useState<VerificationDecision>("VERIFIED");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(
    candidates.length > 0 ? candidates[0].id : null
  );

  const openVerifyModal = (c: RootCauseCandidate, decision: VerificationDecision) => {
    setSelectedCandidate(c);
    setModalDecision(decision);
    setIsModalOpen(true);
  };

  const getConfidenceBarColor = (score: number) => {
    if (score >= 0.75) return "bg-emerald-500";
    if (score >= 0.5) return "bg-amber-500";
    return "bg-slate-500";
  };

  const getVerificationBadge = (c: RootCauseCandidate) => {
    switch (c.verificationStatus) {
      case "VERIFIED":
        return <Badge variant="verified">✓ VERIFIED</Badge>;
      case "REJECTED":
        return <Badge variant="rejected">✕ REJECTED</Badge>;
      case "NEEDS_MORE_EVIDENCE":
        return <Badge variant="needs-evidence">? NEEDS EVIDENCE</Badge>;
      default:
        return <Badge variant="neutral">PENDING VERIFICATION</Badge>;
    }
  };

  if (!candidates || candidates.length === 0) {
    return (
      <div className="p-8 text-center bg-slate-900/40 border border-slate-800 rounded-md">
        <ShieldAlert className="w-8 h-8 text-slate-500 mx-auto mb-2" />
        <h4 className="text-sm font-medium text-slate-300">No Root-Cause Candidates Available</h4>
        <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
          AI investigation has not completed or found insufficient evidence to form candidate hypotheses.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {candidates.map((c, index) => {
        const isExpanded = expandedId === c.id;
        const confidencePct = Math.round(c.confidenceScore * 100);

        return (
          <div
            key={c.id}
            className={clsx(
              "bg-slate-900/80 border rounded-lg overflow-hidden transition-all duration-150",
              c.verificationStatus === "VERIFIED"
                ? "border-emerald-500/40 shadow-emerald-950/20"
                : c.verificationStatus === "REJECTED"
                ? "border-rose-900/30 opacity-75"
                : index === 0
                ? "border-indigo-500/40 shadow-indigo-950/20"
                : "border-slate-800"
            )}
          >
            {/* Header */}
            <div
              onClick={() => setExpandedId(isExpanded ? null : c.id)}
              className="p-4 flex items-center justify-between cursor-pointer hover:bg-slate-800/40 transition gap-4"
            >
              <div className="flex items-center gap-3 min-w-0">
                <span
                  className={clsx(
                    "w-6 h-6 rounded flex items-center justify-center font-mono font-bold text-xs flex-shrink-0",
                    index === 0
                      ? "bg-indigo-600 text-white shadow"
                      : "bg-slate-800 text-slate-300 border border-slate-700"
                  )}
                >
                  #{index + 1}
                </span>

                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono font-bold text-xs text-indigo-400 bg-indigo-950/50 px-2 py-0.5 rounded border border-indigo-800/40">
                      {c.serviceName}
                    </span>
                    {getVerificationBadge(c)}
                  </div>
                  <p className="text-sm font-semibold text-slate-100 mt-1 truncate">
                    {c.hypothesis}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-4 flex-shrink-0">
                {/* Confidence Bar */}
                <div className="text-right">
                  <div className="flex items-center justify-end gap-1.5 font-mono text-xs">
                    <span className="text-slate-400 text-[11px]">Confidence</span>
                    <span className="font-bold text-slate-200">{confidencePct}%</span>
                  </div>
                  <div className="w-24 h-1.5 bg-slate-800 rounded-full overflow-hidden mt-1">
                    <div
                      className={clsx("h-full rounded-full", getConfidenceBarColor(c.confidenceScore))}
                      style={{ width: `${confidencePct}%` }}
                    />
                  </div>
                </div>

                <div className="text-slate-500">
                  {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </div>
              </div>
            </div>

            {/* Expanded Body */}
            {isExpanded && (
              <div className="px-4 pb-4 pt-2 border-t border-slate-800/70 space-y-4 text-xs">
                {/* Detailed Reasoning */}
                <div>
                  <h5 className="font-mono uppercase tracking-wider text-[10px] text-slate-400 mb-1">
                    Investigation Reasoning
                  </h5>
                  <p className="text-slate-300 bg-slate-950/60 p-3 rounded border border-slate-800/60 leading-relaxed">
                    {c.reasoning}
                  </p>
                </div>

                {/* Evidence Grid: Supporting vs Counter */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {/* Supporting Evidence */}
                  <div className="bg-slate-950/40 p-3 rounded border border-slate-800/60">
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-mono text-[11px] font-semibold text-emerald-400 flex items-center gap-1.5">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                        Supporting Evidence ({c.supportingEvidenceIds?.length || 0})
                      </span>
                    </div>

                    {c.supportingEvidenceIds && c.supportingEvidenceIds.length > 0 ? (
                      <div className="space-y-1.5">
                        {c.supportingEvidenceIds.map((eid) => {
                          const ev = allEvidence.find((e) => e.id === eid);
                          return (
                            <div
                              key={eid}
                              onClick={() => onSelectEvidence?.(eid)}
                              className="p-2 rounded bg-slate-900 border border-slate-800 hover:border-slate-700 cursor-pointer flex items-center justify-between group transition"
                            >
                              <div className="truncate mr-2">
                                <span className="font-mono text-[10px] text-indigo-400 block">
                                  {ev?.evidenceType || "EVIDENCE"}
                                </span>
                                <span className="text-slate-300 text-[11px] truncate block">
                                  {ev?.summary || eid}
                                </span>
                              </div>
                              <Link2 className="w-3 h-3 text-slate-500 group-hover:text-slate-300 flex-shrink-0" />
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="text-slate-500 text-[11px] italic">No supporting evidence linked.</p>
                    )}
                  </div>

                  {/* Counter Evidence */}
                  <div className="bg-slate-950/40 p-3 rounded border border-slate-800/60">
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-mono text-[11px] font-semibold text-rose-400 flex items-center gap-1.5">
                        <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                        Counter Evidence ({c.counterEvidenceIds?.length || 0})
                      </span>
                    </div>

                    {c.counterEvidenceIds && c.counterEvidenceIds.length > 0 ? (
                      <div className="space-y-1.5">
                        {c.counterEvidenceIds.map((eid) => {
                          const ev = allEvidence.find((e) => e.id === eid);
                          return (
                            <div
                              key={eid}
                              onClick={() => onSelectEvidence?.(eid)}
                              className="p-2 rounded bg-slate-900 border border-slate-800 hover:border-slate-700 cursor-pointer flex items-center justify-between group transition"
                            >
                              <div className="truncate mr-2">
                                <span className="font-mono text-[10px] text-rose-400 block">
                                  {ev?.evidenceType || "COUNTER_EVIDENCE"}
                                </span>
                                <span className="text-slate-300 text-[11px] truncate block">
                                  {ev?.summary || eid}
                                </span>
                              </div>
                              <Link2 className="w-3 h-3 text-slate-500 group-hover:text-slate-300 flex-shrink-0" />
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="text-slate-500 text-[11px] italic">No disconfirming evidence identified.</p>
                    )}
                  </div>
                </div>

                {/* Recommended Actions */}
                {c.recommendedActions && c.recommendedActions.length > 0 && (
                  <div>
                    <h5 className="font-mono uppercase tracking-wider text-[10px] text-slate-400 mb-1.5">
                      Recommended Remediation Actions
                    </h5>
                    <div className="space-y-1">
                      {c.recommendedActions.map((act, i) => (
                        <div
                          key={i}
                          className="flex items-start gap-2 p-2 rounded bg-slate-950/50 border border-slate-800 text-slate-200"
                        >
                          <ArrowRight className="w-3.5 h-3.5 text-indigo-400 mt-0.5 flex-shrink-0" />
                          <span>{act}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Human Verification Metadata if recorded */}
                {c.verifiedBy && (
                  <div className="p-2.5 rounded bg-slate-950 border border-slate-800 text-[11px] space-y-1">
                    <div className="flex items-center justify-between text-slate-400">
                      <span>
                        Verified by: <strong className="text-slate-200">{c.verifiedBy}</strong>
                      </span>
                      <span>{c.verifiedAt ? new Date(c.verifiedAt).toLocaleString() : ""}</span>
                    </div>
                    {c.feedbackNotes && (
                      <div className="text-slate-300 mt-1 italic font-mono">
                        &quot;{c.feedbackNotes}&quot;
                      </div>
                    )}
                  </div>
                )}

                {/* Operator Actions Bar */}
                {canVerify && (
                  <div className="pt-2 flex items-center justify-end gap-2 border-t border-slate-800/80">
                    <Button
                      variant="outline"
                      size="xs"
                      onClick={() => openVerifyModal(c, "NEEDS_MORE_EVIDENCE")}
                      leftIcon={<HelpCircle className="w-3.5 h-3.5 text-amber-400" />}
                    >
                      Needs Evidence
                    </Button>
                    <Button
                      variant="outline"
                      size="xs"
                      onClick={() => openVerifyModal(c, "REJECTED")}
                      leftIcon={<XCircle className="w-3.5 h-3.5 text-rose-400" />}
                    >
                      Reject
                    </Button>
                    <Button
                      variant="primary"
                      size="xs"
                      onClick={() => openVerifyModal(c, "VERIFIED")}
                      leftIcon={<CheckCircle2 className="w-3.5 h-3.5 text-emerald-300" />}
                    >
                      Verify Root Cause
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}

      {/* Verification Modal Dialog */}
      <VerificationModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        incidentId={incidentId}
        candidate={selectedCandidate}
        initialDecision={modalDecision}
        onVerified={onVerificationComplete}
      />
    </div>
  );
};
