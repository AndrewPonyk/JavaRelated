import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, authStore } from "../api/client";

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

describe("api client", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("parses successful responses", async () => {
    const devices = [
      {
        device_id: "dev-1",
        name: "Sensor",
        site: "lab",
        device_type: "t",
        enabled: true,
        created_at: null,
      },
    ];
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(devices)));

    const result = await api.listDevices();
    expect(result).toHaveLength(1);
    expect(result[0].device_id).toBe("dev-1");
  });

  it("throws ApiError carrying the backend's unified error message", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(
            { error: { code: "backend_unavailable", message: "Cassandra is down" } },
            503,
          ),
        ),
    );

    await expect(api.listDevices()).rejects.toMatchObject({
      name: "ApiError",
      status: 503,
      message: "Cassandra is down",
    });
  });

  it("attaches the bearer token from authStore", async () => {
    authStore.save("token-123", ["admin"]);
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    await api.listDevices();
    const [, init] = fetchMock.mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer token-123");
  });

  it("authStore round-trips roles and clears cleanly", () => {
    authStore.save("t", ["operator", "viewer"]);
    expect(authStore.token).toBe("t");
    expect(authStore.roles).toEqual(["operator", "viewer"]);
    authStore.clear();
    expect(authStore.token).toBeNull();
    expect(authStore.roles).toEqual([]);
  });

  it("ApiError is a proper Error subclass", () => {
    const err = new ApiError(404, "nope");
    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(404);
  });
});
