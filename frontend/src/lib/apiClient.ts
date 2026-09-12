export interface ApiErrorResponse {
  code: string;
  message: string;
  details?: Record<string, any>;
  traceId?: string;
}

export class ApiError extends Error {
  code: string;
  status: number;
  details?: Record<string, any>;
  traceId?: string;

  constructor(status: number, message: string, code: string = "UNKNOWN_ERROR", details?: Record<string, any>, traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
    this.traceId = traceId;
  }
}

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

function generateTraceId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return "trace-" + Math.random().toString(36).substring(2, 15);
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${BASE_URL}${endpoint.startsWith("/") ? endpoint : `/${endpoint}`}`;
  
  let token: string | null = null;
  let tenantId: string | null = null;

  if (typeof window !== "undefined") {
    token = localStorage.getItem("resolveiq_token");
    tenantId = localStorage.getItem("resolveiq_tenant_id");
  }

  const traceId = generateTraceId();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Trace-Id": traceId,
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (tenantId) {
    headers["X-Tenant-ID"] = tenantId;
  }

  const startTime = Date.now();
  let response: Response;

  try {
    response = await fetch(url, {
      ...options,
      headers,
    });
  } catch (err: any) {
    const elapsed = Date.now() - startTime;
    console.error(`[API Network Failure] ${options.method || "GET"} ${url} (${elapsed}ms):`, err);
    throw new ApiError(0, err.message || "Network error. Backend service is unreachable.", "NETWORK_UNREACHABLE", undefined, traceId);
  }

  const duration = Date.now() - startTime;
  const returnedTraceId = response.headers.get("X-Trace-Id") || traceId;

  let body: any = null;
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    try {
      body = await response.json();
    } catch {
      body = null;
    }
  } else {
    try {
      body = await response.text();
    } catch {
      body = null;
    }
  }

  if (!response.ok) {
    const errCode = body?.error?.code || `HTTP_${response.status}`;
    const errMessage = body?.error?.message || body?.message || response.statusText || "Request failed";
    const details = body?.error?.details || body?.details;
    console.error(`[API Error ${response.status}] ${options.method || "GET"} ${url} (${duration}ms):`, errMessage);
    throw new ApiError(response.status, errMessage, errCode, details, returnedTraceId);
  }

  // Handle ApiResponse structure from ResolveIQ backend
  if (body && typeof body === "object") {
    if ("success" in body) {
      if (body.success === false) {
        throw new ApiError(
          response.status,
          body.error?.message || "Operation failed",
          body.error?.code || "OPERATION_FAILED",
          body.error?.details,
          returnedTraceId
        );
      }
      if ("data" in body) {
        return body.data as T;
      }
      if ("items" in body) {
        return body.items as T;
      }
    }
  }

  return body as T;
}

export const api = {
  get: <T>(endpoint: string, params?: Record<string, any>) => {
    let query = "";
    if (params) {
      const sp = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== "") {
          sp.append(k, String(v));
        }
      });
      const str = sp.toString();
      if (str) query = `?${str}`;
    }
    return apiRequest<T>(`${endpoint}${query}`, { method: "GET" });
  },

  getItems: async <T>(endpoint: string, params?: Record<string, any>): Promise<T[]> => {
    const res = await api.get<any>(endpoint, params);
    if (Array.isArray(res)) return res;
    if (res && Array.isArray(res.items)) return res.items;
    if (res && Array.isArray(res.data)) return res.data;
    return [];
  },

  post: <T>(endpoint: string, data?: any) =>
    apiRequest<T>(endpoint, {
      method: "POST",
      body: data !== undefined ? JSON.stringify(data) : undefined,
    }),

  put: <T>(endpoint: string, data?: any) =>
    apiRequest<T>(endpoint, {
      method: "PUT",
      body: data !== undefined ? JSON.stringify(data) : undefined,
    }),

  patch: <T>(endpoint: string, data?: any) =>
    apiRequest<T>(endpoint, {
      method: "PATCH",
      body: data !== undefined ? JSON.stringify(data) : undefined,
    }),

  delete: <T>(endpoint: string) =>
    apiRequest<T>(endpoint, {
      method: "DELETE",
    }),
};
