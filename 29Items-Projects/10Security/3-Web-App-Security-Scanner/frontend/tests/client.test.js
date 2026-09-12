/**
 * Tests: HTTP client — error envelopes, auth headers, silent refresh,
 * terminal auth failure handling. fetch is stubbed globally.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  api,
  ApiError,
  clearTokens,
  hasSession,
  onAuthFailure,
  setTokens,
} from '../src/api/client.js'

const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  clearTokens()
  localStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('error handling', () => {
  it('parses the backend error envelope into ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(403, { error: { code: 'forbidden', message: 'role too low' } })),
    )
    const err = await api.createScan({}).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.code).toBe('forbidden')
    expect(err.message).toBe('role too low')
    expect(err.status).toBe(403)
  })

  it('normalizes network failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('fetch failed'))),
    )
    const err = await api.listScans().catch((e) => e)
    expect(err.code).toBe('network_error')
    expect(err.status).toBe(0)
  })

  it('returns null for 204 responses', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Response(null, { status: 204 })))
    expect(await api.revokeApiKey(7)).toBeNull()
  })
})

describe('auth token attachment', () => {
  it('sends the Bearer token when a session exists', async () => {
    setTokens({ access_token: 'acc-1', refresh_token: 'ref-1' })
    const fetchMock = vi.fn(() => jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    await api.listScans()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBe('Bearer acc-1')
  })

  it('sends no Authorization header without a session', async () => {
    const fetchMock = vi.fn(() => jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    await api.listScans()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBeUndefined()
  })
})

describe('silent refresh on 401', () => {
  it('refreshes once and retries the original request', async () => {
    setTokens({ access_token: 'stale', refresh_token: 'ref-ok' })
    const fetchMock = vi.fn(async (url) => {
      if (String(url).endsWith('/auth/refresh')) {
        return jsonResponse(200, { access_token: 'fresh', refresh_token: 'ref-2' })
      }
      // first data call fails on the stale token, retry succeeds
      return fetchMock.__dataCalls++ === 0
        ? jsonResponse(401, { error: { code: 'http_401', message: 'expired' } })
        : jsonResponse(200, [{ id: 1 }])
    })
    fetchMock.__dataCalls = 0
    vi.stubGlobal('fetch', fetchMock)

    const result = await api.listScans()

    expect(result).toEqual([{ id: 1 }])
    expect(fetchMock).toHaveBeenCalledTimes(3) // data + refresh + retry
    const retryInit = fetchMock.mock.calls[2][1]
    expect(retryInit.headers.Authorization).toBe('Bearer fresh')
  })

  it('clears the session and fires the handler when refresh fails', async () => {
    expect(hasSession()).toBe(false)
    setTokens({ access_token: 'stale', refresh_token: 'ref-bad' })
    const failure = vi.fn()
    onAuthFailure(failure)

    const fetchMock = vi.fn(async (url) => {
      if (String(url).endsWith('/auth/refresh')) {
        return jsonResponse(401, { error: { code: 'http_401', message: 'revoked' } })
      }
      return jsonResponse(401, { error: { code: 'http_401', message: 'expired' } })
    })
    vi.stubGlobal('fetch', fetchMock)

    const err = await api.listScans().catch((e) => e)

    expect(err.status).toBe(401) // surfaced after refresh attempt
    expect(hasSession()).toBe(false)
    expect(failure).toHaveBeenCalledTimes(1)
  })

  it('does not attempt refresh for login failures', async () => {
    const fetchMock = vi.fn(() =>
      jsonResponse(401, { error: { code: 'http_401', message: 'Invalid email or password' } }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const err = await api.login({ email: 'x@y.dev', password: 'wrong-wrong' }).catch((e) => e)

    expect(err.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1) // no refresh, no retry
  })
})
