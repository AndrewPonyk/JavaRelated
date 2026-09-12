import type { ProblemDetails } from '../types/inventory'

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly errors?: Record<string, string>,
  ) {
    super(message)
  }
}

interface RequestOptions extends RequestInit {
  idempotencyKey?: string
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = sessionStorage.getItem('inventory.accessToken')
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey)

  let response: Response
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 15_000)
  const abort = () => controller.abort()
  options.signal?.addEventListener('abort', abort, { once: true })
  try {
    response = await fetch(baseUrl + path, {
      ...options,
      cache: 'no-store',
      credentials: 'omit',
      headers,
      signal: controller.signal,
    })
  } catch (cause) {
    const message = controller.signal.aborted
      ? 'Request timed out or was cancelled'
      : cause instanceof Error
        ? cause.message
        : 'Network request failed'
    throw new ApiError(message, 0, 'offline')
  } finally {
    window.clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abort)
  }
  if (!response.ok) {
    if (response.status === 401) {
      sessionStorage.removeItem('inventory.accessToken')
      window.dispatchEvent(new CustomEvent('inventory-auth-expired'))
    }
    const problem = (await response.json().catch(() => ({}))) as ProblemDetails
    throw new ApiError(
      problem.detail ?? problem.title ?? `Request failed (${response.status})`,
      response.status,
      problem.code,
      problem.errors,
    )
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function newIdempotencyKey(): string {
  if (!crypto.randomUUID) throw new Error('This browser cannot generate secure idempotency keys.')
  return crypto.randomUUID()
}
