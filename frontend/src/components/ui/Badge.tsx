import React from "react";
import clsx from "clsx";

export type BadgeVariant =
  | "sev1"
  | "sev2"
  | "sev3"
  | "sev4"
  | "status-detected"
  | "status-investigating"
  | "status-mitigating"
  | "status-resolved"
  | "status-closed"
  | "verified"
  | "rejected"
  | "needs-evidence"
  | "healthy"
  | "degraded"
  | "critical"
  | "neutral";

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  className?: string;
  size?: "sm" | "md";
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = "neutral",
  className,
  size = "sm",
}) => {
  const variantStyles: Record<BadgeVariant, string> = {
    sev1: "bg-red-500/10 text-red-400 border border-red-500/30 font-semibold",
    sev2: "bg-orange-500/10 text-orange-400 border border-orange-500/30 font-semibold",
    sev3: "bg-yellow-500/10 text-yellow-400 border border-yellow-500/30 font-semibold",
    sev4: "bg-blue-500/10 text-blue-400 border border-blue-500/30 font-semibold",
    "status-detected": "bg-purple-500/10 text-purple-400 border border-purple-500/30",
    "status-investigating": "bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 animate-pulse",
    "status-mitigating": "bg-amber-500/10 text-amber-400 border border-amber-500/30",
    "status-resolved": "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30",
    "status-closed": "bg-slate-700/30 text-slate-400 border border-slate-700",
    verified: "bg-emerald-500/10 text-emerald-300 border border-emerald-500/40 font-bold",
    rejected: "bg-rose-500/10 text-rose-400 border border-rose-500/40",
    "needs-evidence": "bg-amber-500/10 text-amber-300 border border-amber-500/40",
    healthy: "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30",
    degraded: "bg-amber-500/10 text-amber-400 border border-amber-500/30",
    critical: "bg-red-500/10 text-red-400 border border-red-500/30",
    neutral: "bg-slate-800 text-slate-300 border border-slate-700",
  };

  const sizeStyles = {
    sm: "text-[11px] px-2 py-0.5 rounded tracking-wide uppercase",
    md: "text-xs px-2.5 py-1 rounded font-medium",
  };

  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1 font-mono select-none",
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
    >
      {children}
    </span>
  );
};

export function getSeverityVariant(severity: string): BadgeVariant {
  switch (severity?.toUpperCase()) {
    case "SEV1":
      return "sev1";
    case "SEV2":
      return "sev2";
    case "SEV3":
      return "sev3";
    case "SEV4":
      return "sev4";
    default:
      return "neutral";
  }
}

export function getStatusVariant(status: string): BadgeVariant {
  switch (status?.toUpperCase()) {
    case "DETECTED":
    case "CORRELATED":
      return "status-detected";
    case "INVESTIGATING":
    case "IDENTIFIED":
      return "status-investigating";
    case "MITIGATING":
      return "status-mitigating";
    case "RESOLVED":
      return "status-resolved";
    case "CLOSED":
      return "status-closed";
    default:
      return "neutral";
  }
}
