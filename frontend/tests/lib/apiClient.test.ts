import { describe, it, expect, vi, beforeEach } from "vitest";
import { api, ApiError } from "@/lib/apiClient";

describe("apiClient", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("attaches Authorization and tenant headers when token is in localStorage", async () => {
    localStorage.setItem("resolveiq_token", "jwt-test-token");
    localStorage.setItem("resolveiq_tenant_id", "tenant-test-id");

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({
        success: true,
        data: { id: "test-item" },
        traceId: "test-trace",
      }),
    });
    global.fetch = mockFetch;

    const result = await api.get<{ id: string }>("/api/v1/test");

    expect(result.id).toBe("test-item");
    expect(mockFetch).toHaveBeenCalledTimes(1);

    const callArgs = mockFetch.mock.calls[0];
    const headers = callArgs[1].headers;
    expect(headers["Authorization"]).toBe("Bearer jwt-test-token");
    expect(headers["X-Tenant-ID"]).toBe("tenant-test-id");
    expect(headers["X-Trace-Id"]).toBeDefined();
  });

  it("unpacks ApiResponse items array correctly in getItems", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({
        success: true,
        items: [{ id: "1" }, { id: "2" }],
        count: 2,
        traceId: "test-trace",
      }),
    });
    global.fetch = mockFetch;

    const items = await api.getItems<{ id: string }>("/api/v1/items");
    expect(items).toHaveLength(2);
    expect(items[0].id).toBe("1");
  });

  it("throws ApiError with status and code when response is not ok", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: "Not Found",
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({
        success: false,
        error: { code: "INCIDENT_NOT_FOUND", message: "Incident does not exist" },
        traceId: "err-trace",
      }),
    });
    global.fetch = mockFetch;

    await expect(api.get("/api/v1/incidents/nonexistent")).rejects.toThrow(
      "Incident does not exist"
    );
  });
});
