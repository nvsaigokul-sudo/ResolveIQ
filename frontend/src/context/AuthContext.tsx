"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { User, Tenant, UserRole } from "@/types";
import { api } from "@/lib/apiClient";

interface AuthContextType {
  user: User | null;
  tenant: Tenant | null;
  availableTenants: Tenant[];
  role: UserRole;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, role?: string, tenantId?: string) => Promise<void>;
  requestOtp: (email: string) => Promise<{ email: string; message: string; expiresInSeconds: number; devOtp?: string }>;
  verifyOtp: (email: string, otp: string) => Promise<void>;
  registerCustomer: (data: { email: string; fullName: string; companyName: string; jobTitle?: string }) => Promise<{
    id: string;
    email: string;
    fullName: string;
    companyName: string;
    status: string;
    message: string;
    verificationToken?: string;
  }>;
  verifyEmail: (token: string) => Promise<{ id: string; email: string; status: string; message: string }>;
  logout: () => void;
  switchRole: (newRole: UserRole) => Promise<void>;
  switchTenant: (tenantId: string) => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [availableTenants, setAvailableTenants] = useState<Tenant[]>([]);
  const [role, setRole] = useState<UserRole>("SRE");
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchTenants = useCallback(async () => {
    try {
      const tenants = await api.getItems<Tenant>("/api/v1/auth/tenants");
      if (tenants && tenants.length > 0) {
        setAvailableTenants(tenants);
        return tenants;
      }
    } catch (e) {
      console.warn("Failed to fetch tenants:", e);
    }
    return [];
  }, []);

  const refreshProfile = useCallback(async () => {
    const token = typeof window !== "undefined" ? localStorage.getItem("resolveiq_token") : null;
    if (!token) {
      setUser(null);
      setIsLoading(false);
      return;
    }

    try {
      const profile = await api.get<{
        userId: string;
        email: string;
        name?: string;
        tenantId: string;
        tenantName?: string;
        role: UserRole;
      }>("/api/v1/auth/me");

      if (profile) {
        const u: User = {
          id: profile.userId,
          email: profile.email,
          name: profile.name || profile.email.split("@")[0],
          role: profile.role || "SRE",
          tenantId: profile.tenantId,
        };
        setUser(u);
        setRole(u.role);
        setTenant({
          id: profile.tenantId,
          name: profile.tenantName || "Acme Telecom",
          slug: (profile.tenantName || "acme").toLowerCase().replace(/\s+/g, "-"),
        });
        localStorage.setItem("resolveiq_tenant_id", profile.tenantId);
      }
    } catch (err) {
      console.warn("Failed to load user profile with current token:", err);
      // Clear expired or invalid token
      localStorage.removeItem("resolveiq_token");
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    async function init() {
      const tenants = await fetchTenants();
      const token = localStorage.getItem("resolveiq_token");

      if (!token && tenants.length > 0) {
        // Auto-login with default SRE engineer for convenience in dev/testing
        try {
          const res = await api.post<{
            token: string;
            userId: string;
            email: string;
            tenantId: string;
            role: string;
          }>("/api/v1/auth/login", {
            email: "sre@resolveiq.io",
            tenantId: tenants[0].id,
            role: "SRE",
          });

          if (res?.token) {
            localStorage.setItem("resolveiq_token", res.token);
            localStorage.setItem("resolveiq_tenant_id", res.tenantId);
          }
        } catch (e) {
          console.warn("Auto-login skipped:", e);
        }
      }

      await refreshProfile();
    }

    init();
  }, [fetchTenants, refreshProfile]);

  const login = async (email: string, userRole: string = "SRE", tenantId?: string) => {
    setIsLoading(true);
    try {
      const targetTenantId = tenantId || availableTenants[0]?.id || "default";
      const res = await api.post<{
        token: string;
        userId: string;
        email: string;
        tenantId: string;
        role: string;
      }>("/api/v1/auth/login", {
        email,
        tenantId: targetTenantId,
        role: userRole,
      });

      if (res?.token) {
        localStorage.setItem("resolveiq_token", res.token);
        localStorage.setItem("resolveiq_tenant_id", res.tenantId);
        await refreshProfile();
      }
    } finally {
      setIsLoading(false);
    }
  };

  const requestOtp = async (email: string) => {
    setIsLoading(true);
    try {
      const res = await api.post<{
        email: string;
        message: string;
        expiresInSeconds: number;
        devOtp?: string;
      }>("/api/v1/auth/otp/request", { email });
      return res;
    } finally {
      setIsLoading(false);
    }
  };

  const verifyOtp = async (email: string, otp: string) => {
    setIsLoading(true);
    try {
      const res = await api.post<{
        accessToken: string;
        tokenType: string;
        tenantId: string;
        userId: string;
        email: string;
        fullName: string;
        role: UserRole;
        organizationName: string;
        organizationSlug: string;
      }>("/api/v1/auth/otp/verify", { email, otp });

      if (res?.accessToken) {
        localStorage.setItem("resolveiq_token", res.accessToken);
        localStorage.setItem("resolveiq_tenant_id", res.tenantId);
        await refreshProfile();
      }
    } finally {
      setIsLoading(false);
    }
  };

  const registerCustomer = async (data: {
    email: string;
    fullName: string;
    companyName: string;
    jobTitle?: string;
  }) => {
    return await api.post<{
      id: string;
      email: string;
      fullName: string;
      companyName: string;
      status: string;
      message: string;
      verificationToken?: string;
    }>("/api/v1/auth/register", data);
  };

  const verifyEmail = async (token: string) => {
    return await api.post<{
      id: string;
      email: string;
      status: string;
      message: string;
    }>("/api/v1/auth/verify-email", { token });
  };

  const logout = () => {
    api.post("/api/v1/auth/logout", {}).catch((e) => console.warn("Logout notification skipped:", e));
    localStorage.removeItem("resolveiq_token");
    localStorage.removeItem("resolveiq_tenant_id");
    setUser(null);
    setTenant(null);
  };

  const switchRole = async (newRole: UserRole) => {
    try {
      const res = await api.post<{
        token: string;
        role: string;
      }>("/api/v1/auth/switch-role", { role: newRole });

      if (res?.token) {
        localStorage.setItem("resolveiq_token", res.token);
        setRole(newRole);
        if (user) {
          setUser({ ...user, role: newRole });
        }
      }
    } catch (err) {
      console.error("Failed to switch role:", err);
      // Optimistic local switch for UI testing
      setRole(newRole);
      if (user) {
        setUser({ ...user, role: newRole });
      }
    }
  };

  const switchTenant = async (newTenantId: string) => {
    localStorage.setItem("resolveiq_tenant_id", newTenantId);
    const found = availableTenants.find((t) => t.id === newTenantId);
    if (found) {
      setTenant(found);
    }
    // Re-login with current role under new tenant
    if (user) {
      await login(user.email, user.role, newTenantId);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        tenant,
        availableTenants,
        role,
        isAuthenticated: !!user,
        isLoading,
        login,
        requestOtp,
        verifyOtp,
        registerCustomer,
        verifyEmail,
        logout,
        switchRole,
        switchTenant,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
