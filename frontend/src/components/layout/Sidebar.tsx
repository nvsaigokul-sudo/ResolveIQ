"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  AlertOctagon,
  Boxes,
  Network,
  Terminal,
  BarChart3,
  GitCommit,
  BookOpen,
  FileCode2,
  Server,
  Share2,
  ShieldCheck,
  Settings,
  Flame,
  Radio,
} from "lucide-react";
import clsx from "clsx";

interface NavItem {
  label: string;
  href: string;
  icon: React.ReactNode;
  badge?: number;
}

export const Sidebar: React.FC<{ activeIncidentsCount?: number }> = ({ activeIncidentsCount }) => {
  const pathname = usePathname();

  const navigation: { section: string; items: NavItem[] }[] = [
    {
      section: "OPERATIONS",
      items: [
        {
          label: "Overview",
          href: "/",
          icon: <Activity className="w-4 h-4" />,
        },
        {
          label: "Incidents",
          href: "/incidents",
          icon: <AlertOctagon className="w-4 h-4" />,
          badge: activeIncidentsCount,
        },
        {
          label: "Services",
          href: "/services",
          icon: <Boxes className="w-4 h-4" />,
        },
        {
          label: "Topology & Blast Radius",
          href: "/dependencies",
          icon: <Network className="w-4 h-4" />,
        },
      ],
    },
    {
      section: "TELEMETRY",
      items: [
        {
          label: "Logs Explorer",
          href: "/logs",
          icon: <Terminal className="w-4 h-4" />,
        },
        {
          label: "Metrics Explorer",
          href: "/metrics",
          icon: <BarChart3 className="w-4 h-4" />,
        },
        {
          label: "Traces Waterfall",
          href: "/traces",
          icon: <Radio className="w-4 h-4" />,
        },
      ],
    },
    {
      section: "CHANGE & KNOWLEDGE",
      items: [
        {
          label: "Deployments",
          href: "/deployments",
          icon: <GitCommit className="w-4 h-4" />,
        },
        {
          label: "Knowledge Base",
          href: "/knowledge",
          icon: <BookOpen className="w-4 h-4" />,
        },
        {
          label: "Runbooks",
          href: "/runbooks",
          icon: <FileCode2 className="w-4 h-4" />,
        },
      ],
    },
    {
      section: "PLATFORM & SECURITY",
      items: [
        {
          label: "Collector Fleet",
          href: "/collectors",
          icon: <Server className="w-4 h-4" />,
        },
        {
          label: "Integrations & DLQ",
          href: "/integrations",
          icon: <Share2 className="w-4 h-4" />,
        },
        {
          label: "Audit Logs",
          href: "/audit-logs",
          icon: <ShieldCheck className="w-4 h-4" />,
        },
        {
          label: "Settings & Quotas",
          href: "/settings",
          icon: <Settings className="w-4 h-4" />,
        },
      ],
    },
  ];

  return (
    <aside className="w-60 bg-slate-950 border-r border-slate-800 flex flex-col h-screen select-none flex-shrink-0">
      {/* Brand Header */}
      <div className="h-14 px-4 flex items-center justify-between border-b border-slate-800/80 bg-slate-950">
        <Link href="/" className="flex items-center gap-2">
          <div className="w-7 h-7 rounded bg-gradient-to-tr from-indigo-600 to-cyan-500 flex items-center justify-center text-white shadow">
            <Flame className="w-4 h-4" />
          </div>
          <div>
            <span className="font-mono font-bold text-sm text-slate-100 tracking-tight">
              Resolve<span className="text-indigo-400">IQ</span>
            </span>
            <span className="text-[9px] uppercase px-1 py-0.2 ml-1 rounded bg-indigo-500/10 text-indigo-400 font-mono border border-indigo-500/30">
              SRE
            </span>
          </div>
        </Link>
      </div>

      {/* Nav List */}
      <div className="flex-1 overflow-y-auto px-3 py-3 space-y-6">
        {navigation.map((group) => (
          <div key={group.section} className="space-y-1">
            <div className="px-2 text-[10px] font-mono tracking-wider font-semibold text-slate-400 uppercase">
              {group.section}
            </div>
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const isActive =
                  item.href === "/"
                    ? pathname === "/"
                    : pathname.startsWith(item.href);

                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={clsx(
                      "flex items-center justify-between px-2.5 py-1.5 rounded text-xs font-medium transition-colors duration-150 group",
                      isActive
                        ? "bg-indigo-600/15 text-indigo-300 border border-indigo-500/30"
                        : "text-slate-400 hover:text-slate-200 hover:bg-slate-900 border border-transparent"
                    )}
                  >
                    <div className="flex items-center gap-2.5">
                      <span
                        className={clsx(
                          "transition-colors",
                          isActive ? "text-indigo-400" : "text-slate-500 group-hover:text-slate-300"
                        )}
                      >
                        {item.icon}
                      </span>
                      <span>{item.label}</span>
                    </div>

                    {item.badge !== undefined && item.badge > 0 && (
                      <span
                        className={clsx(
                          "text-[10px] font-mono px-1.5 py-0.2 rounded-full font-bold",
                          isActive
                            ? "bg-indigo-500 text-white"
                            : "bg-red-500/20 text-red-400 border border-red-500/40"
                        )}
                      >
                        {item.badge}
                      </span>
                    )}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      {/* Footer / System Health Status */}
      <div className="p-3 border-t border-slate-800 bg-slate-900/30">
        <div className="flex items-center justify-between text-[11px] text-slate-400">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-mono text-slate-300">Core Engine: UP</span>
          </div>
          <span className="font-mono text-[10px] text-slate-400">v1.0.0</span>
        </div>
      </div>
    </aside>
  );
};
