/** Auth endpoint wrappers + session lifecycle. */

import type { UserRead } from "../types/api";
import { apiFetch } from "./client";
import { clearTokens, getRefreshToken, setTokens, type TokenPair } from "./tokens";

export async function register(email: string, password: string): Promise<UserRead> {
  return apiFetch<UserRead>(
    "/api/v1/auth/register",
    { method: "POST", body: JSON.stringify({ email, password }) },
    { auth: false },
  );
}

export async function login(email: string, password: string): Promise<void> {
  const pair = await apiFetch<TokenPair>(
    "/api/v1/auth/login",
    { method: "POST", body: JSON.stringify({ email, password }) },
    { auth: false },
  );
  setTokens(pair);
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  clearTokens();
  if (refreshToken) {
    // Best-effort server-side revocation; local state is already cleared.
    try {
      await apiFetch<void>(
        "/api/v1/auth/logout",
        { method: "POST", body: JSON.stringify({ refresh_token: refreshToken }) },
        { auth: false },
      );
    } catch {
      /* revocation is best-effort — the rotated token expires on its own */
    }
  }
}

export async function fetchCurrentUser(): Promise<UserRead> {
  return apiFetch<UserRead>("/api/v1/auth/me");
}
