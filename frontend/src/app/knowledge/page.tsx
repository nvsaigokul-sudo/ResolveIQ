"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/apiClient";
import { Card, CardHeader, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import {
  BookOpen,
  Search,
  Plus,
  RefreshCw,
  Sparkles,
  ExternalLink,
  Tag,
} from "lucide-react";

interface KnowledgeArticle {
  id: string;
  title: string;
  service: string;
  summary: string;
  tags: string[];
  relevanceScore?: number;
  updatedAt: string;
}

export default function KnowledgeBasePage() {
  const [articles, setArticles] = useState<KnowledgeArticle[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  const fetchArticles = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getItems<KnowledgeArticle>("/api/v1/knowledge", {
        query: searchQuery.trim(),
      });
      setArticles(data || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchArticles();
  }, []);

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-indigo-400" />
            Knowledge Base & Postmortems
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Vector-indexed historical incident postmortems, architectural learnings, and RAG ground truth.
          </p>
        </div>

        <Button
          variant="secondary"
          size="xs"
          onClick={fetchArticles}
          leftIcon={<RefreshCw className="w-3 h-3" />}
        >
          Refresh Articles
        </Button>
      </div>

      {error && (
        <ErrorAlert
          title="Failed to Load Knowledge Base"
          message={error.message}
          code={error.code}
          traceId={error.traceId}
          onRetry={fetchArticles}
        />
      )}

      {/* Search Input */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-lg p-3 flex items-center gap-3 text-xs">
        <div className="flex items-center gap-2 flex-1">
          <Search className="w-4 h-4 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && fetchArticles()}
            placeholder="Semantic search historical incidents (e.g. 'Hikari connection pool leak', 'OOM during peak')..."
            className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-sans"
          />
        </div>

        <Button variant="primary" size="xs" onClick={fetchArticles}>
          Search
        </Button>
      </div>

      {/* Articles Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {isLoading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="p-4 bg-slate-900 border border-slate-800 rounded-lg">
              <Skeleton className="h-5 w-3/4 mb-3" />
              <Skeleton lines={3} />
            </div>
          ))
        ) : articles.length > 0 ? (
          articles.map((art) => (
            <div
              key={art.id}
              className="bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg p-4 flex flex-col justify-between transition shadow-sm"
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="font-mono text-xs text-indigo-400 bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-800/40">
                    {art.service}
                  </span>
                  {art.relevanceScore !== undefined && (
                    <span className="text-[10px] font-mono text-emerald-400 flex items-center gap-1">
                      <Sparkles className="w-3 h-3" />
                      {Math.round(art.relevanceScore * 100)}% match
                    </span>
                  )}
                </div>

                <h3 className="text-sm font-bold text-slate-100">{art.title}</h3>
                <p className="text-xs text-slate-400 mt-1.5 leading-relaxed">{art.summary}</p>
              </div>

              <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between">
                <div className="flex items-center gap-1.5 flex-wrap">
                  {art.tags?.map((t) => (
                    <span
                      key={t}
                      className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700 flex items-center gap-1"
                    >
                      <Tag className="w-2.5 h-2.5" />
                      {t}
                    </span>
                  ))}
                </div>

                <span className="text-[10px] font-mono text-slate-500">
                  {new Date(art.updatedAt).toLocaleDateString()}
                </span>
              </div>
            </div>
          ))
        ) : (
          <div className="col-span-full py-16 text-center text-slate-500 text-xs italic bg-slate-900/30 rounded border border-slate-800">
            No historical postmortems or knowledge articles found.
          </div>
        )}
      </div>
    </div>
  );
}
