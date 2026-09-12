import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, auth } from "./client.js";

/** jsdom-safe fetch stub: client.js only touches ok/status/json(). */
function res(body, ok = true, status = 200) {
  return { ok, status, json: async () => body };
}

afterEach(() => {
  vi.unstubAllGlobals();
  auth.setToken(null);
});

describe("ApiError", () => {
  it("carries code, status and correlation id", () => {
    const err = new ApiError("boom", {
      code: "invalid_input",
      status: 422,
      correlationId: "abc",
    });
    expect(err).toBeInstanceOf(Error);
    expect(err.message).toBe("boom");
    expect(err.code).toBe("invalid_input");
    expect(err.status).toBe(422);
    expect(err.correlationId).toBe("abc");
  });

  it("defaults code to unknown and status to 0", () => {
    const err = new ApiError("netfail");
    expect(err.code).toBe("unknown");
    expect(err.status).toBe(0);
  });
});

describe("auth token store", () => {
  it("round-trips a token through sessionStorage", () => {
    expect(auth.getToken()).toBeNull();
    auth.setToken("tok-123");
    expect(auth.getToken()).toBe("tok-123");
    expect(auth.authHeaders()).toEqual({ Authorization: "Bearer tok-123" });
  });

  it("setToken(null) removes the token", () => {
    auth.setToken("temp");
    auth.setToken(null);
    expect(auth.getToken()).toBeNull();
    expect(auth.authHeaders()).toEqual({});
  });
});

describe("apiFetch", () => {
  it("unwraps the {data} envelope", async () => {
    const fetchMock = vi.fn(async () => res({ data: { answer: 42 } }));
    vi.stubGlobal("fetch", fetchMock);

    const out = await apiFetch("/some/endpoint");
    expect(out).toEqual({ answer: 42 });
    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/some/endpoint");
    expect(init.method).toBe("GET");
    expect(init.headers).toEqual({}); // no content-type without a body
  });

  it("sends JSON body and bearer header when auth:true", async () => {
    auth.setToken("tok-xyz");
    const fetchMock = vi.fn(async () => res({ data: {} }));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch("/aes/encrypt", {
      method: "POST",
      body: { a: 1 },
      auth: true,
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/aes/encrypt");
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.headers.Authorization).toBe("Bearer tok-xyz");
    expect(JSON.parse(init.body)).toEqual({ a: 1 });
  });

  it("throws ApiError with server code/message on !ok", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        res(
          {
            error: {
              code: "verification_failed",
              message: "GCM tag mismatch",
              correlation_id: "cid-1",
            },
          },
          false,
          422,
        ),
      ),
    );

    const err = await apiFetch("/aes/decrypt", {
      method: "POST",
      body: {},
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe("verification_failed");
    expect(err.status).toBe(422);
    expect(err.message).toBe("GCM tag mismatch");
    expect(err.correlationId).toBe("cid-1");
  });

  it("falls back to a status-only message when the body is unparseable", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => res(null, false, 500)),
    );

    const err = await apiFetch("/x").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe("Request failed (500)");
  });

  it("maps network failure to a friendly ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("fetch failed"))),
    );

    const err = await apiFetch("/x").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe("network_error");
    expect(err.message).toMatch(/backend running/i);
  });
});
