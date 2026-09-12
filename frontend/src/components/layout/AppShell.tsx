"use client";

import React, { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import { api } from "@/lib/apiClient";
import { Incident } from "@/types";

export const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const pathname = usePathname();
  const isLoginPage = pathname === "/login";

  const [activeIncidentsCount, setActiveIncidentsCount] = useState<number>(0);

  useEffect(() => {
    if (isLoginPage) return;

    let mounted = true;
    const fetchActiveCount = async () => {
      try {
        const incidents = await api.getItems<Incident>("/api/v1/incidents", {
          status: "DETECTED,CORRELATED,INVESTIGATING,IDENTIFIED,MITIGATING",
        });
        if (mounted && Array.isArray(incidents)) {
          setActiveIncidentsCount(incidents.length);
        }
      } catch {
        // Fallback silently if unauthenticated or offline
      }
    };

    fetchActiveCount();
    const interval = setInterval(fetchActiveCount, 30000); // 30s poll
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [isLoginPage]);

  if (isLoginPage) {
    return <main className="min-h-screen bg-slate-950 text-slate-100">{children}</main>;
  }

  return (
    <div className="flex h-screen overflow-hidden bg-slate-950 text-slate-100 antialiased">
      <Sidebar activeIncidentsCount={activeIncidentsCount} />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Topbar />
        <main className="flex-1 overflow-y-auto bg-slate-950 p-6">{children}</main>
      </div>
    </div>
  );
};
