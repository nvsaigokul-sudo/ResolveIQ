"use client";

import React, { useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { RootCauseCandidate, VerificationDecision } from "@/types";
import { api } from "@/lib/apiClient";
import { CheckCircle2, XCircle, HelpCircle, AlertCircle } from "lucide-react";

interface VerificationModalProps {
  isOpen: boolean;
  onClose: () => void;
  incidentId: string;
  candidate: RootCauseCandidate | null;
  initialDecision?: VerificationDecision;
  onVerified: () => void;
}

export const VerificationModal: React.FC<VerificationModalProps> = ({
  isOpen,
  onClose,
  incidentId,
  candidate,
  initialDecision = "VERIFIED",
  onVerified,
}) => {
  const [decision, setDecision] = useState<VerificationDecision>(initialDecision);
  const [notes, setNotes] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Sync initialDecision when opened
  React.useEffect(() => {
    if (isOpen) {
      setDecision(initialDecision);
      setNotes("");
      setError(null);
    }
  }, [isOpen, initialDecision]);

  if (!candidate) return null;

  const handleSubmit = async () => {
    if (decision !== "VERIFIED" && !notes.trim()) {
      setError("Rationale / feedback notes are required when rejecting or requesting more evidence.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await api.post(`/api/v1/incidents/${incidentId}/verify`, {
        candidateId: candidate.id,
        decision,
        feedbackNotes: notes.trim() || (decision === "VERIFIED" ? "Root cause verified by operator" : ""),
      });
      onVerified();
      onClose();
    } catch (err: any) {
      setError(err.message || "Failed to submit human verification");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Human Verification of Root Cause"
      maxWidth="lg"
      footer={
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant={decision === "VERIFIED" ? "primary" : decision === "REJECTED" ? "danger" : "secondary"}
            onClick={handleSubmit}
            isLoading={isSubmitting}
          >
            Submit Decision
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-xs">
        {/* Hypothesis Preview */}
        <div className="p-3 bg-slate-950 border border-slate-800 rounded">
          <div className="flex items-center justify-between mb-1">
            <span className="font-mono text-slate-400">Candidate Target:</span>
            <span className="font-mono font-bold text-indigo-400">{candidate.serviceName}</span>
          </div>
          <p className="font-medium text-slate-200">{candidate.hypothesis}</p>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-slate-400">AI Confidence:</span>
            <span className="font-mono font-bold text-indigo-300">
              {Math.round(candidate.confidenceScore * 100)}%
            </span>
          </div>
        </div>

        {/* Decision Selection */}
        <div>
          <label className="block font-medium text-slate-300 mb-2">
            Verification Decision <span className="text-rose-400">*</span>
          </label>
          <div className="grid grid-cols-3 gap-2">
            <button
              type="button"
              onClick={() => setDecision("VERIFIED")}
              className={`p-2.5 rounded border text-left flex flex-col gap-1 transition ${
                decision === "VERIFIED"
                  ? "bg-emerald-950/40 border-emerald-500/80 text-emerald-300"
                  : "bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200"
              }`}
            >
              <div className="flex items-center gap-1.5 font-semibold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                <span>VERIFIED</span>
              </div>
              <span className="text-[10px] text-slate-400">Root cause is confirmed accurate.</span>
            </button>

            <button
              type="button"
              onClick={() => setDecision("REJECTED")}
              className={`p-2.5 rounded border text-left flex flex-col gap-1 transition ${
                decision === "REJECTED"
                  ? "bg-rose-950/40 border-rose-500/80 text-rose-300"
                  : "bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200"
              }`}
            >
              <div className="flex items-center gap-1.5 font-semibold">
                <XCircle className="w-4 h-4 text-rose-400" />
                <span>REJECTED</span>
              </div>
              <span className="text-[10px] text-slate-400">Hypothesis is invalid / false lead.</span>
            </button>

            <button
              type="button"
              onClick={() => setDecision("NEEDS_MORE_EVIDENCE")}
              className={`p-2.5 rounded border text-left flex flex-col gap-1 transition ${
                decision === "NEEDS_MORE_EVIDENCE"
                  ? "bg-amber-950/40 border-amber-500/80 text-amber-300"
                  : "bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200"
              }`}
            >
              <div className="flex items-center gap-1.5 font-semibold">
                <HelpCircle className="w-4 h-4 text-amber-400" />
                <span>NEED EVIDENCE</span>
              </div>
              <span className="text-[10px] text-slate-400">Inconclusive; inspect telemetry.</span>
            </button>
          </div>
        </div>

        {/* Feedback Notes */}
        <div>
          <label className="block font-medium text-slate-300 mb-1.5">
            SRE Feedback & Rationale{" "}
            {decision !== "VERIFIED" && <span className="text-rose-400 font-normal">(Required)</span>}
          </label>
          <textarea
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={
              decision === "VERIFIED"
                ? "Optional validation notes, verified log line, or confirmation context..."
                : "Explain why this hypothesis is rejected or what specific evidence is missing..."
            }
            className="w-full bg-slate-950 border border-slate-800 rounded p-2.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />
        </div>

        {/* Error message */}
        {error && (
          <div className="p-2.5 rounded bg-rose-950/40 border border-rose-800 text-rose-300 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>
    </Modal>
  );
};
