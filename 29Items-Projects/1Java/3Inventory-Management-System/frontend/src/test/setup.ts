import { afterEach, beforeEach, vi } from 'vitest'

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  vi.stubGlobal('crypto', { randomUUID: vi.fn(() => '00000000-0000-4000-8000-000000000001') })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
