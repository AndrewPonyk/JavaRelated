import axios from 'axios'

/**
 * Central axios instance for the v1 API.
 *  - Injects the bearer token on every request.
 *  - Transparently honours 429 Retry-After with a single back-off retry.
 *  - Normalizes the server's error envelope (see ARCHITECTURE.md §2.6).
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  headers: { Accept: 'application/json' },
  timeout: 15000,
})

// ── Request: attach auth token ────────────────────────────────────────────
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Response: 429 back-off + error normalization ──────────────────────────
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { response, config } = error

    // Respect rate limiting: wait Retry-After, then retry once.
    if (response?.status === 429 && !config.__retried) {
      const retryAfter = Number(response.headers['retry-after'] ?? 1)
      config.__retried = true
      await new Promise((r) => setTimeout(r, retryAfter * 1000))
      return api(config)
    }

    // Surface a consistent shape to callers.
    return Promise.reject({
      status: response?.status ?? 0,
      message: response?.data?.message ?? 'Network error',
      errors: response?.data?.errors ?? null,
      code: response?.data?.error_code ?? 'NETWORK_ERROR',
    })
  },
)

export default api
