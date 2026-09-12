"use client";

import React, { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { UserRole } from "@/types";
import {
  Building2,
  Shield,
  Search,
  User,
  LogOut,
  ChevronDown,
  Sparkles,
} from "lucide-react";
import clsx from "clsx";

const ROLES: UserRole[] = [
  "SRE",
  "INCIDENT_COMMANDER",
  "ADMIN",
  "OWNER",
  "SECURITY_ENGINEER",
  "VIEWER",
];

export const Topbar: React.FC = () => {
  const { user, tenant, availableTenants, role, switchRole, switchTenant, logout } =
    useAuth();

  const [roleDropdownOpen, setRoleDropdownOpen] = useState(false);
  const [tenantDropdownOpen, setTenantDropdownOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "/" && (e.target as HTMLElement).tagName !== "INPUT") {
        e.preventDefault();
        document.getElementById("global-search-input")?.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <header className="h-14 bg-slate-950/80 backdrop-blur border-b border-slate-800 px-4 flex items-center justify-between gap-4 z-30 sticky top-0">
      {/* Search Input */}
      <div className="flex-1 max-w-md relative">
        <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
        <input
          id="global-search-input"
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search incidents, services, traces, runbooks... (Press '/' to focus)"
          className="w-full bg-slate-900 border border-slate-800 rounded pl-9 pr-8 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 font-sans"
        />
        <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[10px] font-mono text-slate-500 bg-slate-800 px-1 py-0.2 rounded border border-slate-700">
          /
        </kbd>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-3">
        {/* Tenant Switcher */}
        <div className="relative">
          <button
            onClick={() => {
              setTenantDropdownOpen(!tenantDropdownOpen);
              setRoleDropdownOpen(false);
              setUserDropdownOpen(false);
            }}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-medium text-slate-200"
          >
            <Building2 className="w-3.5 h-3.5 text-indigo-400" />
            <span className="truncate max-w-[120px]">{tenant?.name || "Select Tenant"}</span>
            <ChevronDown className="w-3 h-3 text-slate-500" />
          </button>

          {tenantDropdownOpen && (
            <div className="absolute right-0 mt-1 w-56 bg-slate-900 border border-slate-800 rounded shadow-xl py-1 z-50 animate-in fade-in zoom-in-95 duration-100">
              <div className="px-3 py-1.5 text-[10px] font-mono text-slate-400 uppercase tracking-wider border-b border-slate-800">
                Switch Organization
              </div>
              {availableTenants.length > 0 ? (
                availableTenants.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => {
                      switchTenant(t.id);
                      setTenantDropdownOpen(false);
                    }}
                    className={clsx(
                      "w-full text-left px-3 py-1.5 text-xs flex items-center justify-between hover:bg-slate-800 transition",
                      tenant?.id === t.id ? "text-indigo-400 font-medium" : "text-slate-300"
                    )}
                  >
                    <span>{t.name}</span>
                    <span className="text-[10px] font-mono text-slate-400">{t.slug}</span>
                  </button>
                ))
              ) : (
                <div className="px-3 py-2 text-xs text-slate-400">Default Tenant Active</div>
              )}
            </div>
          )}
        </div>

        {/* Live RBAC Role Switcher */}
        <div className="relative">
          <button
            onClick={() => {
              setRoleDropdownOpen(!roleDropdownOpen);
              setTenantDropdownOpen(false);
              setUserDropdownOpen(false);
            }}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-slate-900 hover:bg-slate-800 border border-indigo-500/30 text-xs font-mono text-indigo-300"
            title="Live RBAC Role Simulator"
          >
            <Shield className="w-3.5 h-3.5 text-indigo-400" />
            <span className="font-semibold">{role}</span>
            <span className="text-[9px] px-1 py-0.2 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
              RBAC
            </span>
            <ChevronDown className="w-3 h-3 text-slate-500" />
          </button>

          {roleDropdownOpen && (
            <div className="absolute right-0 mt-1 w-64 bg-slate-900 border border-slate-800 rounded shadow-xl py-1 z-50 animate-in fade-in zoom-in-95 duration-100">
              <div className="px-3 py-1.5 text-[10px] font-mono text-slate-400 uppercase tracking-wider border-b border-slate-800 flex items-center justify-between">
                <span>Simulate Active Role</span>
                <Sparkles className="w-3 h-3 text-indigo-400" />
              </div>
              {ROLES.map((r) => (
                <button
                  key={r}
                  onClick={() => {
                    switchRole(r);
                    setRoleDropdownOpen(false);
                  }}
                  className={clsx(
                    "w-full text-left px-3 py-2 text-xs flex items-center justify-between hover:bg-slate-800 transition font-mono",
                    role === r ? "text-indigo-400 bg-indigo-500/10 font-bold" : "text-slate-300"
                  )}
                >
                  <span>{r}</span>
                  {role === r && <span className="text-[10px] text-indigo-400 font-sans">Active</span>}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* User Profile */}
        <div className="relative">
          <button
            onClick={() => {
              setUserDropdownOpen(!userDropdownOpen);
              setRoleDropdownOpen(false);
              setTenantDropdownOpen(false);
            }}
            className="flex items-center gap-2 p-1.5 rounded hover:bg-slate-900 transition"
          >
            <div className="w-7 h-7 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 font-mono text-xs">
              {user?.name ? user.name.slice(0, 2).toUpperCase() : <User className="w-3.5 h-3.5" />}
            </div>
          </button>

          {userDropdownOpen && (
            <div className="absolute right-0 mt-1 w-60 bg-slate-900 border border-slate-800 rounded shadow-xl py-1 z-50">
              <div className="px-3 py-2 border-b border-slate-800">
                <p className="text-xs font-medium text-slate-200">{user?.name || "SRE Operator"}</p>
                <p className="text-[11px] font-mono text-slate-400 truncate">{user?.email}</p>
                <p className="text-[10px] font-mono text-indigo-400 mt-0.5">Role: {role}</p>
              </div>

              <button
                onClick={() => {
                  logout();
                  setUserDropdownOpen(false);
                }}
                className="w-full text-left px-3 py-2 text-xs text-rose-400 hover:bg-rose-950/20 hover:text-rose-300 flex items-center gap-2 transition"
              >
                <LogOut className="w-3.5 h-3.5" />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
