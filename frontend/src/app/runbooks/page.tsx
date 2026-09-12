"use client";

import React, { useState, useEffect } from "react";
import { Runbook, Service } from "@/types";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  FileCode2,
  Search,
  CheckCircle2,
  Terminal,
  ExternalLink,
  Tag,
  BookOpen,
} from "lucide-react";
import clsx from "clsx";

export default function RunbooksPage() {
  const [runbooks, setRunbooks] = useState<Runbook[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [selectedRunbook, setSelectedRunbook] = useState<Runbook | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchRunbooks = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getItems<Runbook>("/api/v1/runbooks", {
        query: searchQuery.trim(),
      });
      setRunbooks(data || []);
      if (data && data.length > 0 && !selectedRunbook) {
        setSelectedRunbook(data[0]);
      }
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchRunbooks();
  }, []);

  const filtered = runbooks.filter((rb) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      rb.title?.toLowerCase().includes(q) ||
      rb.service?.toLowerCase().includes(q) ||
      rb.content?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <FileCode2 className="w-5 h-5 text-indigo-400" />
            Runbooks & SOP Manager
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Operational runbooks, emergency mitigation procedures, and automated execution steps.
          </p>
        </div>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Runbooks"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchRunbooks}
        />
      )}

      {/* Two-Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Runbooks List */}
        <div className="space-y-3">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search runbooks..."
              className="w-full bg-slate-900 border border-slate-800 rounded pl-8 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
            />
          </div>

          <div className="space-y-1.5 max-h-[600px] overflow-y-auto pr-1">
            {isLoading ? (
              <Skeleton lines={5} />
            ) : filtered.length > 0 ? (
              filtered.map((rb) => {
                const isSelected = selectedRunbook?.id === rb.id;
                return (
                  <button
                    key={rb.id}
                    onClick={() => setSelectedRunbook(rb)}
                    className={clsx(
                      "w-full text-left p-3 rounded-lg border transition text-xs",
                      isSelected
                        ? "bg-indigo-950/40 border-indigo-500/80 text-indigo-200 shadow-sm"
                        : "bg-slate-900 border-slate-800 text-slate-300 hover:border-slate-700"
                    )}
                  >
                    <div className="flex items-center justify-between gap-1 mb-1">
                      <span className="font-mono text-[10px] text-indigo-400 bg-indigo-950/80 px-1.5 py-0.2 rounded border border-indigo-800">
                        {rb.service}
                      </span>
                      <span className="text-[10px] font-mono text-slate-500">
                        {new Date(rb.updatedAt).toLocaleDateString()}
                      </span>
                    </div>
                    <div className="font-semibold text-slate-100 truncate">{rb.title}</div>
                  </button>
                );
              })
            ) : (
              <div className="text-xs text-slate-500 italic p-4 text-center">
                No runbooks found.
              </div>
            )}
          </div>
        </div>

        {/* Right: Markdown Content Viewer */}
        <div className="lg:col-span-2">
          <Card className="border-slate-800">
            {selectedRunbook ? (
              <>
                <CardHeader
                  title={
                    <div className="flex items-center gap-2">
                      <span>{selectedRunbook.title}</span>
                      <span className="font-mono text-xs text-indigo-400">
                        ({selectedRunbook.service})
                      </span>
                    </div>
                  }
                  subtitle={`Last reviewed: ${new Date(selectedRunbook.updatedAt).toLocaleString()}`}
                />
                <CardBody className="p-5 font-mono text-xs text-slate-300 leading-relaxed space-y-4">
                  <div className="flex items-center gap-1.5 pb-3 border-b border-slate-800 flex-wrap">
                    {selectedRunbook.tags?.map((t) => (
                      <span
                        key={t}
                        className="text-[10px] px-2 py-0.5 rounded bg-slate-950 text-slate-400 border border-slate-800"
                      >
                        #{t}
                      </span>
                    ))}
                  </div>

                  <div className="prose prose-invert max-w-none text-slate-300 whitespace-pre-wrap font-sans text-xs">
                    {selectedRunbook.content}
                  </div>
                </CardBody>
              </>
            ) : (
              <div className="py-24 text-center text-slate-500 text-xs italic">
                Select a runbook from the left panel to inspect remediation procedures.
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
