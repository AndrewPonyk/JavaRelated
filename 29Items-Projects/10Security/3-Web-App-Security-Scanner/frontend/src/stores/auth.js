/**
 * Minimal reactive auth store. Vue Router is the only state-adjacent dep in
 * this app by design (no Pinia) — a module-scope reactive + exported
 * actions keeps the surface testable without another dependency.
 *
 * The store owns the CURRENT USER; raw tokens live in api/client.js so the
 * HTTP layer stays the single place that touches Authorization headers.
 */

import { reactive, readonly } from 'vue'
import { api, clearTokens, hasSession, onAuthFailure, setTokens } from '../api/client.js'

const USER_KEY = 'wss.user'

const state = reactive({
  user: readUser(),
  ready: false, // initial /me reconciliation finished
})

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) ?? null
  } catch {
    return null
  }
}

function persistUser(user) {
  state.user = user
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
    else localStorage.removeItem(USER_KEY)
  } catch {
    /* storage unavailable — keep in-memory only */
  }
}

/** Readonly view for components (mutations only via the actions below). */
export const authStore = readonly(state)

export const isAuthenticated = () => state.user !== null || hasSession()
export const isAdmin = () => state.user?.role === 'admin'
export const canScan = () => ['scanner', 'admin'].includes(state.user?.role)

export async function login(email, password) {
  const tokens = await api.login({ email, password })
  setTokens(tokens)
  persistUser(await api.me())
}

export async function register(payload) {
  await api.register(payload) // backend creates viewer-role accounts
  await login(payload.email, payload.password)
}

export function logout() {
  clearTokens()
  persistUser(null)
}

/**
 * Re-validate the cached session on app start. A stored token pair whose
 * /me call fails (expired/revoked) is dropped instead of trapping the user
 * in a broken authenticated shell.
 */
export async function bootstrap() {
  if (hasSession() && !state.user) {
    try {
      persistUser(await api.me())
    } catch {
      logout()
    }
  }
  state.ready = true
}

/**
 * Wire the client's terminal-auth-failure signal to a router redirect.
 * The router is injected here to avoid a circular import (router → store
 * for guards, store → router for redirects).
 */
export function wireAuthFailure(router) {
  onAuthFailure(() => {
    logout()
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  })
}
