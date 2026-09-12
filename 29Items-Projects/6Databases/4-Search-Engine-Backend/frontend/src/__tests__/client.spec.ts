import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiGet, apiPost, buildQuery } from "@/api/client";

function mockFetch(response: Partial<Response> & { jsonBody?: unknown }): typeof fetch {
  const impl = vi.fn(async () => ({
    ok: response.ok ?? true,
    status: response.status ?? 200,
    statusText: response.statusText ?? "OK",
    headers: new Headers(),
    json: async () => response.jsonBody,
  })) as unknown as typeof fetch;
  vi.stubGlobal("fetch", impl);
  return impl;
}

afterEach(() => vi.unstubAllGlobals());

describe("buildQuery", () => {
  it("serializes arrays as repeated params and skips empty values", () => {
    const qs = buildQuery({
      q: "tv stand",
      brand: ["sony", "lg"],
      page: 1,
      empty: "",
      missing: undefined,
      nil: null,
    });
    expect(qs).toBe("?q=tv+stand&brand=sony&brand=lg&page=1");
  });

  it("returns an empty string when there is nothing to send", () => {
    expect(buildQuery({})).toBe("");
  });
});

describe("apiGet / apiPost", () => {
  it("sends the session header and parses JSON", async () => {
    const fetchMock = mockFetch({ jsonBody: { hello: "world" } });

    const result = await apiGet<{ hello: string }>("/search", { q: "tv" });

    expect(result).toEqual({ hello: "world" });
    const [url, init] = vi.mocked(fetchMock).mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toContain("/search?q=tv");
    expect((init.headers as Record<string, string>)["X-Session-ID"]).toBeTruthy();
  });

  it("throws a typed ApiError from RFC-7807 problem bodies", async () => {
    mockFetch({
      ok: false,
      status: 422,
      statusText: "Unprocessable",
      jsonBody: { detail: "bad range", code: "invalid_search_query", request_id: "r1" },
    });

    const error = (await apiGet("/search").catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.message).toBe("bad range");
    expect(error.code).toBe("invalid_search_query");
    expect(error.requestId).toBe("r1");
  });

  it("returns undefined for 204 responses (click events)", async () => {
    mockFetch({ status: 204 });
    await expect(apiPost<void>("/events/click", { query: "tv" })).resolves.toBeUndefined();
  });
});
