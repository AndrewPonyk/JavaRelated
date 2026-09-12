/**
 * Token storage. Access token lives in memory only (gone on refresh — that's
 * fine, we re-mint from the refresh token). The refresh token persists in
 * localStorage: an accepted tradeoff for an education SPA without a BFF —
 * it is single-use (rotation) and server-revocable, which bounds theft value.
 */

const REFRESH_KEY = "scp.refreshToken";

let accessToken: string | null = null;

export interface TokenPair {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
}

export function setTokens(pair: TokenPair): void {
  accessToken = pair.access_token;
  localStorage.setItem(REFRESH_KEY, pair.refresh_token);
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function clearTokens(): void {
  accessToken = null;
  localStorage.removeItem(REFRESH_KEY);
}

export function hasSession(): boolean {
  return getRefreshToken() !== null;
}
