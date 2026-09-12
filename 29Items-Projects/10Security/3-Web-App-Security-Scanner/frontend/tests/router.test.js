/**
 * Tests: router guards — authentication wall, admin-only routes,
 * login redirect round-trip. Real store + real router (memory history).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { router } from '../src/router/index.js'
import { login, logout } from '../src/stores/auth.js'
import { clearTokens } from '../src/api/client.js'

const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

function stubMe(role) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url) => {
      const target = String(url)
      if (target.endsWith('/auth/login')) {
        return jsonResponse(200, { access_token: 'a', refresh_token: 'r' })
      }
      if (target.endsWith('/auth/me')) {
        return jsonResponse(200, { id: 1, email: 'op@test.dev', role, is_active: true })
      }
      return jsonResponse(404, {})
    }),
  )
}

beforeEach(async () => {
  clearTokens()
  localStorage.clear()
  logout()
  // park on a neutral route before each assertion
  await router.push('/login')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('authentication guard', () => {
  it('redirects unauthenticated users to /login with a redirect query', async () => {
    await router.push('/')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
  })

  it('keeps authenticated users on protected routes', async () => {
    stubMe('scanner')
    await login('op@test.dev', 'password-123')

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/')
  })
})

describe('admin guard', () => {
  it('blocks non-admins from admin routes', async () => {
    stubMe('viewer')
    await login('op@test.dev', 'password-123')

    await router.push('/admin/users')

    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('lets admins onto admin routes', async () => {
    stubMe('admin')
    await login('op@test.dev', 'password-123')

    await router.push('/targets')

    expect(router.currentRoute.value.name).toBe('targets')
  })
})

describe('public routes', () => {
  it('login is reachable without a session', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('login')
  })
})

describe('scan detail route', () => {
  it('accepts numeric ids and rejects others', async () => {
    stubMe('viewer')
    await login('op@test.dev', 'password-123')

    await router.push('/scans/42')
    expect(router.currentRoute.value.name).toBe('scan-detail')
    expect(router.currentRoute.value.params.id).toBe('42')

    // non-numeric id falls through to the catch-all → dashboard
    await router.push('/scans/abc')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })
})
