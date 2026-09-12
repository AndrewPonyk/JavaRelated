import { computed, ref } from 'vue'

const token = ref(sessionStorage.getItem('inventory.accessToken') ?? '')
const localAuth = import.meta.env.VITE_LOCAL_AUTH === 'true'

window.addEventListener('inventory-auth-expired', () => {
  token.value = ''
})

interface TokenClaims {
  exp?: number
  scope?: string
  scp?: string[]
}

function decodeClaims(value: string): TokenClaims | undefined {
  if (!value) return undefined
  try {
    const segment = value.split('.')[1]
    if (!segment) return undefined
    const normalized = segment
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(Math.ceil(segment.length / 4) * 4, '=')
    return JSON.parse(atob(normalized)) as TokenClaims
  } catch {
    return undefined
  }
}

export function useAuth() {
  const claims = computed(() => decodeClaims(token.value))
  const validToken = computed(() =>
    Boolean(claims.value?.exp && claims.value.exp * 1000 > Date.now()),
  )
  const scopes = computed(() => {
    if (localAuth && !token.value)
      return new Set(['inventory:read', 'inventory:write', 'inventory:admin'])
    if (!validToken.value) return new Set<string>()
    return new Set(claims.value?.scp ?? claims.value?.scope?.split(' ').filter(Boolean) ?? [])
  })
  function setToken(value: string) {
    token.value = value.trim()
    if (token.value) sessionStorage.setItem('inventory.accessToken', token.value)
    else sessionStorage.removeItem('inventory.accessToken')
  }
  return {
    token,
    isAuthenticated: computed(() => localAuth || validToken.value),
    canWrite: computed(
      () => scopes.value.has('inventory:write') || scopes.value.has('inventory:admin'),
    ),
    isAdmin: computed(() => scopes.value.has('inventory:admin')),
    setToken,
  }
}
