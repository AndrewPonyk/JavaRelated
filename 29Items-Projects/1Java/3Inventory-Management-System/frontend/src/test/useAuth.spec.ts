import { describe, expect, it } from 'vitest'
import { useAuth } from '../composables/useAuth'

function token(payload: object): string {
  return `x.${btoa(JSON.stringify(payload)).replace(/=/g, '')}.x`
}

describe('authorization state', () => {
  it('does not grant production permissions without a token', () => {
    expect(useAuth().isAuthenticated.value).toBe(false)
    expect(useAuth().isAdmin.value).toBe(false)
  })

  it('derives permissions from a valid token and keeps it in session storage', () => {
    const auth = useAuth()
    auth.setToken(
      token({ scope: 'inventory:read inventory:write', exp: Math.floor(Date.now() / 1000) + 60 }),
    )
    expect(auth.isAuthenticated.value).toBe(true)
    expect(auth.canWrite.value).toBe(true)
    expect(auth.isAdmin.value).toBe(false)
    auth.setToken('')
    expect(sessionStorage.getItem('inventory.accessToken')).toBeNull()
  })

  it('rejects malformed and expired tokens', () => {
    const auth = useAuth()
    auth.setToken('invalid')
    expect(auth.canWrite.value).toBe(false)
    auth.setToken(token({ scope: 'inventory:admin', exp: 1 }))
    expect(auth.isAdmin.value).toBe(false)
  })

  it('revokes in-memory authorization after an API authentication failure', () => {
    const auth = useAuth()
    auth.setToken(token({ scope: 'inventory:admin', exp: Math.floor(Date.now() / 1000) + 60 }))
    expect(auth.isAdmin.value).toBe(true)

    window.dispatchEvent(new CustomEvent('inventory-auth-expired'))

    expect(auth.isAuthenticated.value).toBe(false)
    expect(auth.isAdmin.value).toBe(false)
    auth.setToken('')
  })
})
