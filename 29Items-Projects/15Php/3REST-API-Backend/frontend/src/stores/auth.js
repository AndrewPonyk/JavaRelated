import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/services/api'

/**
 * Auth store: holds the Sanctum token + current user, persists the token to
 * localStorage, and exposes login / register / logout actions.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(null)
  const user = ref(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  /** Restore a persisted session on app boot. */
  function init() {
    const saved = localStorage.getItem('token')
    if (saved) {
      token.value = saved
      // Validate the token by loading the current user; drop it if invalid.
      fetchMe().catch(() => logout())
    }
  }

  function setToken(value) {
    token.value = value
    localStorage.setItem('token', value)
  }

  async function login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    setToken(data.token)
    user.value = data.user
  }

  async function register(payload) {
    const { data } = await api.post('/auth/register', payload)
    setToken(data.token)
    user.value = data.user
  }

  async function fetchMe() {
    const { data } = await api.get('/auth/me')
    user.value = data.user
  }

  async function logout() {
    try {
      if (token.value) {
        await api.post('/auth/logout')
      }
    } catch {
      // Ignore network/401 errors — we clear local state regardless.
    } finally {
      token.value = null
      user.value = null
      localStorage.removeItem('token')
    }
  }

  return { token, user, isAuthenticated, init, login, register, fetchMe, logout }
})
