import { describe, expect, it, vi } from "vitest";

import { api, ApiError, fetchJson } from "./client";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("fetchJson", () => {
  it("returns parsed JSON on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(200, { total: 3 })));
    await expect(fetchJson<{ total: number }>("/api/v1/datasets")).resolves.toEqual({ total: 3 });
  });

  it("throws ApiError with the problem detail on failure", async () => {
    // fresh Response per call — a body can only be consumed once
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse(409, { detail: "dataset 'x' already exists" }))),
    );
    await expect(fetchJson("/api/v1/datasets")).rejects.toBeInstanceOf(ApiError);
    await expect(fetchJson("/api/v1/datasets")).rejects.toMatchObject({
      status: 409,
      message: "dataset 'x' already exists",
    });
  });

  it("returns undefined for 204 responses", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(api.delete("/api/v1/datasets/abc")).resolves.toBeUndefined();
  });

  it("sends JSON bodies with the right method", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { id: "d-1" }));
    vi.stubGlobal("fetch", fetchMock);

    await api.post("/api/v1/datasets", { name: "sales.orders" });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/v1/datasets");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ name: "sales.orders" });
  });

  it("attaches the bearer token from localStorage when present", async () => {
    localStorage.setItem("lakehouse_token", "tok-123");
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);

    await api.get("/api/v1/datasets");
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer tok-123");
    localStorage.removeItem("lakehouse_token");
  });
});
