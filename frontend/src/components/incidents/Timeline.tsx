"use client";

import React, { useState } from "react";
import { IncidentEvent } from "@/types";
import { api } from "@/lib/apiClient";
import { Clock, Send, ShieldAlert, Cpu, CheckCircle2, MessageSquare, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import clsx from "clsx";

interface TimelineProps {
  incidentId: string;
  events: IncidentEvent[];
  onEventAdded?: () => void;
  canComment?: boolean;
}

export const Timeline: React.FC<TimelineProps> = ({
  incidentId,
  events,
  onEventAdded,
  canComment = true,
}) => {
  const [comment, setComment] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!comment.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      await api.post(`/api/v1/incidents/${incidentId}/events`, {
        eventType: "HUMAN_COMMENT",
        payload: { text: comment.trim() },
      });
      setComment("");
      onEventAdded?.();
    } catch (err: any) {
      setError(err.message || "Failed to append comment");
    } finally {
      setIsSubmitting(false);
    }
  };

  const getEventIcon = (type: string) => {
    switch (type) {
      case "INCIDENT_CREATED":
        return <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />;
      case "ANOMALY_CORRELATED":
        return <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />;
      case "AI_INVESTIGATION_STARTED":
      case "AI_INVESTIGATION_COMPLETED":
        return <Cpu className="w-3.5 h-3.5 text-indigo-400" />;
      case "HUMAN_VERIFICATION":
        return <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />;
      case "HUMAN_COMMENT":
        return <MessageSquare className="w-3.5 h-3.5 text-cyan-400" />;
      default:
        return <Clock className="w-3.5 h-3.5 text-slate-400" />;
    }
  };

  const formatTime = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    } catch {
      return iso;
    }
  };

  return (
    <div className="space-y-4">
      {/* Event Feed */}
      <div className="relative pl-6 space-y-4 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-[1px] before:bg-slate-800">
        {events && events.length > 0 ? (
          events.map((evt, idx) => (
            <div key={evt.id || idx} className="relative group">
              {/* Timeline Marker */}
              <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-slate-900 border border-slate-700 flex items-center justify-center shadow">
                {getEventIcon(evt.eventType)}
              </div>

              <div className="bg-slate-900/60 border border-slate-800/80 rounded p-3 text-xs">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span className="font-mono font-semibold text-slate-200">
                    {evt.eventType.replace(/_/g, " ")}
                  </span>
                  <div className="flex items-center gap-2 text-[11px] font-mono text-slate-400">
                    <span>{evt.actor || "system"}</span>
                    <span>•</span>
                    <span>{formatTime(evt.createdAt)}</span>
                  </div>
                </div>

                {evt.payload && (
                  <div className="text-slate-300 font-sans mt-1">
                    {evt.payload.text ? (
                      <p className="whitespace-pre-wrap">{evt.payload.text}</p>
                    ) : evt.payload.summary ? (
                      <p>{evt.payload.summary}</p>
                    ) : evt.payload.hypothesis ? (
                      <p className="font-mono text-[11px] text-indigo-300">
                        Hypothesis: {evt.payload.hypothesis}
                      </p>
                    ) : (
                      <pre className="text-[10px] font-mono text-slate-400 overflow-x-auto bg-slate-950 p-1.5 rounded mt-1">
                        {JSON.stringify(evt.payload, null, 2)}
                      </pre>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))
        ) : (
          <div className="text-xs text-slate-500 py-3 italic">
            No timeline events recorded yet.
          </div>
        )}
      </div>

      {/* Add Comment Input */}
      {canComment && (
        <form onSubmit={handleAddComment} className="pt-2">
          {error && <div className="text-xs text-rose-400 mb-2 font-mono">{error}</div>}
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Add SRE operational note or hypothesis (Press Enter)..."
              disabled={isSubmitting}
              className="flex-1 bg-slate-900 border border-slate-800 rounded px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
            />
            <Button
              type="submit"
              variant="secondary"
              size="sm"
              isLoading={isSubmitting}
              disabled={!comment.trim()}
              leftIcon={<Send className="w-3.5 h-3.5" />}
            >
              Post
            </Button>
          </div>
        </form>
      )}
    </div>
  );
};
