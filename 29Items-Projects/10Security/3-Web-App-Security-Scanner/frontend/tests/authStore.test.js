/**
 * Tests: auth store — login/logout state transitions and session
 * reconciliation on bootstrap. fetch is stubbed globally.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  authStore,
  bootstrap,
  isAdmin,
  isAuthenticated,
  login,
  logout,
  register,
} from '../src/stores/auth.js'
import { clearTokens, setTokens } from '../src/api/client.js'

const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  logout() // clears BOTH the reactive user and the client's token state
  localStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubAuthFlow(user) {
  return vi.fn(async (url, init) => {
    const target = String(url)
    if (target.endsWith('/auth/login')) {
      return jsonResponse(200, { access_token: 'a', refresh_token: 'r', token_type: 'bearer' })
    }
    if (target.endsWith('/auth/register')) {
      return jsonResponse(201, { id: 1, email: user.email, role: 'viewer', is_active: true })
    }
    if (target.endsWith('/auth/me')) {
      return jsonResponse(200, user)
    }
    return jsonResponse(404, {})
  })
}

describe('login', () => {
  it('stores tokens and the /me user', async () => {
    const user = { id: 5, email: 'op@test.dev', role: 'scanner', is_active: true }
    vi.stubGlobal('fetch', stubAuthFlow(user))

    await login('op@test.dev', 'password-123')

    expect(authStore.user).toEqual(user)
    expect(isAuthenticated()).toBe(true)
    expect(isAdmin()).toBe(false)
    // persisted for reloads
    expect(JSON.parse(localStorage.getItem('wss.user')).email).toBe('op@test.dev')
  })

  it('login failure leaves the store clean', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(401, { error: { code: 'http_401', message: 'Invalid email or password' } })),
    )

    await expect(login('op@test.dev', 'nope')).rejects.toThrow('Invalid email or password')

    expect(authStore.user).toBeNull()
    expect(isAuthenticated()).toBe(false)
  })
})

describe('register', () => {
  it('registers then signs in automatically', async () => {
    const user = { id: 9, email: 'new@test.dev', role: 'viewer', is_active: true }
    const fetchMock = stubAuthFlow(user)
    vi.stubGlobal('fetch', fetchMock)

    await register({ email: 'new@test.dev', password: 'long-enough-pw', full_name: 'New' })

    expect(authStore.user).toEqual(user)
    const calls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(calls.some((c) => c.endsWith('/auth/register'))).toBe(true)
    expect(calls.some((c) => c.endsWith('/auth/login'))).toBe(true)
  })
})

describe('logout', () => {
  it('clears user and persisted state', async () => {
    vi.stubGlobal('fetch', stubAuthFlow({ id: 1, email: 'x@x.dev', role: 'admin', is_active: true }))
    await login('x@x.dev', 'password-123')
    expect(isAdmin()).toBe(true)

    logout()

    expect(authStore.user).toBeNull()
    expect(isAuthenticated()).toBe(false)
    expect(localStorage.getItem('wss.user')).toBeNull()
    expect(localStorage.getItem('wss.tokens')).toBeNull()
  })
})

describe('bootstrap', () => {
  it('reconciles a cached token pair via /me', async () => {
    setTokens({ access_token: 'a', refresh_token: 'r' })
    const user = { id: 2, email: 'cached@test.dev', role: 'viewer', is_active: true }
    vi.stubGlobal('fetch', stubAuthFlow(user))

    await bootstrap()

    expect(authStore.ready).toBe(true)
    expect(authStore.user).toEqual(user)
  })

  it('drops a stale session instead of trapping the user', async () => {
    setTokens({ access_token: 'expired', refresh_token: 'dead' })
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(401, { error: { code: 'http_401', message: 'expired' } })),
    )

    await bootstrap()

    expect(authStore.ready).toBe(true)
    expect(authStore.user).toBeNull()
    expect(isAuthenticated()).toBe(false)
  })
})
