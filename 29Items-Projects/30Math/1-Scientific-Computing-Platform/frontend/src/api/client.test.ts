import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "./client";
import { clearTokens, setTokens } from "./tokens";

const tokenPair = (suffix: string) => ({
  access_token: `access-${suffix}`,
  refresh_token: `refresh-${suffix}`,
  token_type: "bearer",
  expires_in: 900,
});

afterEach(() => {
  vi.unstubAllGlobals();
  clearTokens();
  localStorage.clear();
});

describe("api client", () => {
  it("attaches the bearer token to authenticated requests", async () => {
    setTokens(tokenPair("1"));
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchSpy);

    await apiFetch("/api/v1/computations");

    const headers = new Headers(fetchSpy.mock.calls[0][1].headers);
    expect(headers.get("Authorization")).toBe("Bearer access-1");
  });

  it("refreshes once on 401 and retries the original request", async () => {
    setTokens(tokenPair("old"));
    const fetchSpy = vi
      .fn()
      // 1) original request → 401
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      // 2) refresh call → new pair
      .mockResolvedValueOnce(
        new Response(JSON.stringify(tokenPair("new")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      // 3) retried request → success
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ items: [] }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchSpy);

    const body = await apiFetch<{ items: unknown[] }>("/api/v1/computations");

    expect(body.items).toEqual([]);
    expect(fetchSpy).toHaveBeenCalledTimes(3);
    const retryHeaders = new Headers(fetchSpy.mock.calls[2][1].headers);
    expect(retryHeaders.get("Authorization")).toBe("Bearer access-new");
  });

  it("throws an ApiError carrying the problem detail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            type: "https://scp.example.com/problems/expression_parse_error",
            title: "expression parse error",
            status: 422,
            detail: "Expression is empty.",
            request_id: "r-1",
          }),
          { status: 422, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    await expect(apiFetch("/api/v1/symbolic/render")).rejects.toMatchObject({
      name: "ApiError",
      status: 422,
      message: "Expression is empty.",
    });
  });
});
