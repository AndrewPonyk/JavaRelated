// Shared helper for /app/api/* route handlers: run a backend call and translate
// ApiClientError into the same JSON error envelope the backend uses.
import { NextResponse } from "next/server";

import { ApiClientError } from "@/lib/api";

export async function proxy<T>(fn: () => Promise<T>): Promise<NextResponse> {
  try {
    const data = await fn();
    return NextResponse.json(data ?? { ok: true });
  } catch (err) {
    if (err instanceof ApiClientError) {
      return NextResponse.json(
        { error: { code: err.code, message: err.message } },
        { status: err.status },
      );
    }
    return NextResponse.json(
      { error: { code: "proxy_error", message: "Upstream request failed." } },
      { status: 502 },
    );
  }
}

export async function parseJson<T>(request: Request): Promise<T> {
  return (await request.json()) as T;
}
