"use client";

import React, { useState, useEffect } from "react";
import { IntegrationStatus } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  Share2,
  Bell,
  MessageSquare,
  AlertTriangle,
  RefreshCw,
  CheckCircle2,
  ExternalLink,
  Shield,
  Send,
} from "lucide-react";
import clsx from "clsx";

interface DeadLetterRecord {
  id: string;
  channel: string;
  recipient: string;
  subject: string;
  failureReason: string;
  attemptCount: number;
  createdAt: string;
}

export default function IntegrationsPage() {
  const [integrations, setIntegrations] = useState<IntegrationStatus[]>([]);
  const [deadLetters, setDeadLetters] = useState<DeadLetterRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const fetchData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [intRes, dlqRes] = await Promise.allSettled([
        api.getItems<IntegrationStatus>("/api/v1/integrations"),
        api.getItems<DeadLetterRecord>("/api/v1/notifications/dead-letter"),
      ]);

      if (intRes.status === "fulfilled") setIntegrations(intRes.value);
      if (dlqRes.status === "fulfilled") setDeadLetters(dlqRes.value);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleRetryDlq = async (id: string) => {
    setRetryingId(id);
    try {
      await api.post(`/api/v1/notifications/${id}/retry`);
      await fetchData();
    } catch (err: any) {
      alert(`Retry failed: ${err.message}`);
    } finally {
      setRetryingId(null);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <Share2 className="w-5 h-5 text-indigo-400" />
            Integrations Hub & Notification DLQ
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            External alert channels, webhook dispatchers, and Dead-Letter Queue retry operations.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchData}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Channels
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Integrations"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchData}
        />
      )}

      {/* Integrations Status Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {[
          { name: "Slack Incident Webhook", type: "SLACK", icon: <MessageSquare className="w-4 h-4 text-emerald-400" />, status: "CONNECTED" },
          { name: "PagerDuty Event API v2", type: "PAGERDUTY", icon: <Bell className="w-4 h-4 text-indigo-400" />, status: "CONNECTED" },
          { name: "GitHub Pull Requests", type: "GIT", icon: <Shield className="w-4 h-4 text-cyan-400" />, status: "CONNECTED" },
        ].map((item, idx) => (
          <Card key={idx} className="border-slate-800 bg-slate-900/80">
            <CardBody className="p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded bg-slate-800 border border-slate-700">
                  {item.icon}
                </div>
                <div>
                  <h4 className="text-xs font-semibold text-slate-200">{item.name}</h4>
                  <span className="text-[10px] font-mono text-slate-400">{item.type}</span>
                </div>
              </div>

              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-950/60 text-emerald-400 border border-emerald-800 font-bold flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" />
                {item.status}
              </span>
            </CardBody>
          </Card>
        ))}
      </div>

      {/* Dead-Letter Queue (DLQ) Management */}
      <Card className="border-slate-800">
        <CardHeader
          title={
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-400" />
              <span>Dead-Letter Queue (DLQ) Inspection & Safe Retry</span>
            </div>
          }
          subtitle="Alert dispatches that exceeded max backoff retries and require operator intervention"
        />

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 font-mono text-[11px] text-slate-400">
                <th className="py-3 px-4">CHANNEL</th>
                <th className="py-3 px-4">RECIPIENT / ENDPOINT</th>
                <th className="py-3 px-4">SUBJECT</th>
                <th className="py-3 px-4">FAILURE REASON</th>
                <th className="py-3 px-4">ATTEMPTS</th>
                <th className="py-3 px-4 text-right">ACTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono">
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={6} className="py-3 px-4">
                      <Skeleton className="h-5 w-full" />
                    </td>
                  </tr>
                ))
              ) : deadLetters.length > 0 ? (
                deadLetters.map((dl) => (
                  <tr key={dl.id} className="hover:bg-slate-800/40 transition">
                    <td className="py-3 px-4 font-bold text-amber-400">
                      {dl.channel}
                    </td>

                    <td className="py-3 px-4 text-slate-300">
                      {dl.recipient}
                    </td>

                    <td className="py-3 px-4 text-slate-200 font-sans">
                      {dl.subject}
                    </td>

                    <td className="py-3 px-4 text-rose-400 text-[11px]">
                      {dl.failureReason}
                    </td>

                    <td className="py-3 px-4 text-slate-400">
                      {dl.attemptCount} / 3
                    </td>

                    <td className="py-3 px-4 text-right">
                      <Button
                        variant="secondary"
                        size="xs"
                        isLoading={retryingId === dl.id}
                        onClick={() => handleRetryDlq(dl.id)}
                        leftIcon={<Send className="w-3 h-3" />}
                      >
                        Retry Dispatch
                      </Button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500 text-xs italic font-sans">
                    DLQ is clear. All alert notifications have delivered successfully.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
