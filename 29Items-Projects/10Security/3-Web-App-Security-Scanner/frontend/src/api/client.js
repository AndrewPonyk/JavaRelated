/**
 * Single HTTP client for the scanner API — the ONLY place that knows about
 * auth headers, token refresh, and error envelopes. Components never call
 * fetch directly.
 *
 * Tokens live in localStorage ("wss.tokens"); the access token is attached
 * to every request, and a 401 triggers exactly one silent refresh + retry
 * (single-flight: parallel 401s share one refresh call) before the session
 * is discarded and the registered auth-failure handler is notified.
 */

const BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const TOKENS_KEY = 'wss.tokens'

export class ApiError extends Error {
  constructor(code, message, status) {
    super(message)
    this.code = code
    this.status = status
  }
}

// ── token storage ─────────────────────────────────────────────────────

let tokens = readTokens()

function readTokens() {
  try {
    return JSON.parse(localStorage.getItem(TOKENS_KEY)) ?? null
  } catch {
    return null
  }
}

export function setTokens(next) {
  tokens = next
  try {
    localStorage.setItem(TOKENS_KEY, JSON.stringify(next))
  } catch {
    /* storage unavailable (private mode) — session-only auth */
  }
}

export function clearTokens() {
  tokens = null
  try {
    localStorage.removeItem(TOKENS_KEY)
  } catch {
    /* ignore */
  }
}

export function getAccessToken() {
  return tokens?.access_token ?? null
}

export function hasSession() {
  return tokens !== null
}

// Fired once, after a refresh definitively fails — the auth store turns
// this into a redirect to /login.
let authFailureHandler = null

export function onAuthFailure(cb) {
  authFailureHandler = cb
}

// ── silent refresh (single-flight) ────────────────────────────────────

let refreshInFlight = null

async function tryRefresh() {
  if (!tokens?.refresh_token) return false
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const resp = await fetch(`${BASE}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refresh_token: tokens.refresh_token }),
        })
        if (!resp.ok) return null
        const next = await resp.json()
        setTokens(next)
        return next
      } catch {
        return null
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return (await refreshInFlight) !== null
}

// ── core request ──────────────────────────────────────────────────────

function authHeaders() {
  return tokens?.access_token ? { Authorization: `Bearer ${tokens.access_token}` } : {}
}

async function parseError(resp) {
  const body = await resp.json().catch(() => ({}))
  const err = body.error ?? { code: 'unknown', message: `HTTP ${resp.status}` }
  return new ApiError(err.code ?? 'unknown', err.message ?? resp.statusText, resp.status)
}

async function rawRequest(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...authHeaders(), ...options.headers }
  let resp
  try {
    resp = await fetch(`${BASE}${path}`, { ...options, headers })
  } catch {
    throw new ApiError('network_error', 'Cannot reach scanner API', 0)
  }
  if (!resp.ok) throw await parseError(resp)
  return resp.status === 204 ? null : resp.json()
}

const AUTH_PATHS = ['/auth/login', '/auth/refresh', '/auth/register']

async function request(path, options = {}, retried = false) {
  try {
    return await rawRequest(path, options)
  } catch (err) {
    const refreshable =
      err instanceof ApiError && err.status === 401 && !retried && hasSession() &&
      !AUTH_PATHS.some((p) => path.startsWith(p))
    if (refreshable) {
      if (await tryRefresh()) return request(path, options, true)
      clearTokens()
      authFailureHandler?.()
    }
    throw err
  }
}

// ── authenticated file download (HTML / SARIF reports) ────────────────

export async function downloadFile(path, filename) {
  let resp
  try {
    resp = await fetch(`${BASE}${path}`, { headers: authHeaders() })
  } catch {
    throw new ApiError('network_error', 'Cannot reach scanner API', 0)
  }
  if (!resp.ok) throw await parseError(resp)

  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

// ── endpoint surface (mirrors the OpenAPI spec) ───────────────────────

export const api = {
  // auth
  register: (payload) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request('/auth/me'),

  // api keys
  listApiKeys: () => request('/auth/api-keys'),
  createApiKey: (payload) =>
    request('/auth/api-keys', { method: 'POST', body: JSON.stringify(payload) }),
  revokeApiKey: (id) => request(`/auth/api-keys/${id}`, { method: 'DELETE' }),

  // users (admin)
  listUsers: () => request('/users'),
  updateUser: (id, payload) =>
    request(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),

  // scan-target allowlist
  listTargets: () => request('/targets'),
  createTarget: (payload) =>
    request('/targets', { method: 'POST', body: JSON.stringify(payload) }),
  verifyTarget: (id) => request(`/targets/${id}/verify`, { method: 'POST' }),
  deleteTarget: (id) => request(`/targets/${id}`, { method: 'DELETE' }),

  // scans
  createScan: (payload) =>
    request('/scans', { method: 'POST', body: JSON.stringify(payload) }),
  listScans: (params = '') => request(`/scans${params}`),
  getScan: (id) => request(`/scans/${id}`),
  scanProgress: (id) => request(`/scans/${id}/progress`),
  cancelScan: (id) => request(`/scans/${id}/cancel`, { method: 'POST' }),
  scanFindingCounts: (id) => request(`/scans/${id}/findings/count`),

  // findings
  listFindings: (params = '') => request(`/findings${params}`),
  getFinding: (id) => request(`/findings/${id}`),
  severityStats: (scanId) =>
    request(`/findings/stats/severity${scanId ? `?scan_id=${scanId}` : ''}`),

  // reports
  downloadReport: (scanId, format) =>
    downloadFile(
      `/reports/${scanId}/${format}`,
      `scan-${scanId}-report.${format === 'sarif' ? 'sarif.json' : 'html'}`,
    ),
}
