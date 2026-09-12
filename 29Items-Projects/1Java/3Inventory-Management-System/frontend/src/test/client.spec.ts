import { describe, expect, it, vi } from 'vitest'
import { apiRequest, ApiError, newIdempotencyKey } from '../api/client'

describe('API client', () => {
  it('sends authentication and idempotency headers', async () => {
    sessionStorage.setItem('inventory.accessToken', 'token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    await expect(
      apiRequest('/api/test', { method: 'POST', body: '{}', idempotencyKey: 'key' }),
    ).resolves.toEqual({ ok: true })
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('Idempotency-Key')).toBe('key')
  })

  it('maps problem details and network failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ detail: 'No stock', code: 'stock' }), { status: 422 }),
        ),
    )
    await expect(apiRequest('/api/test')).rejects.toMatchObject({
      status: 422,
      code: 'stock',
      message: 'No stock',
    })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    await expect(apiRequest('/api/test')).rejects.toEqual(new ApiError('offline', 0, 'offline'))
  })

  it('handles empty responses and generates keys', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(apiRequest('/api/test')).resolves.toBeUndefined()
    expect(newIdempotencyKey()).toContain('00000000')
  })

  it('clears an expired session after an unauthorized response', async () => {
    sessionStorage.setItem('inventory.accessToken', 'expired')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))
    await expect(apiRequest('/api/test')).rejects.toMatchObject({ status: 401 })
    expect(sessionStorage.getItem('inventory.accessToken')).toBeNull()
  })
})
