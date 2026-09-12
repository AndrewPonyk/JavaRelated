import { describe, expect, it, vi } from 'vitest'
import { enqueueCommand, flushQueue, readQueue } from '../composables/offlineQueue'

describe('offline command queue', () => {
  it('stores and successfully replays commands', async () => {
    enqueueCommand('item-1', 'receipts', { quantity: 2 })
    expect(readQueue()).toHaveLength(1)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 200 })))
    await expect(flushQueue()).resolves.toEqual({ completed: 1, remaining: 0 })
    expect(readQueue()).toHaveLength(0)
  })

  it('retains a command after a network error', async () => {
    enqueueCommand('item-1', 'shipments', { quantity: 2 })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    await expect(flushQueue()).resolves.toEqual({ completed: 0, remaining: 1 })
    expect(readQueue()[0].attempts).toBe(1)
  })

  it('discards permanently invalid commands and corrupt storage', async () => {
    enqueueCommand('item-1', 'adjustments', { delta: -2 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 422 })))
    await expect(flushQueue()).resolves.toEqual({ completed: 0, remaining: 0 })
    localStorage.setItem('inventory.offlineCommands', 'broken')
    expect(readQueue()).toEqual([])
  })

  it('filters well-formed but unsafe persisted commands', () => {
    localStorage.setItem(
      'inventory.offlineCommands',
      JSON.stringify([
        {
          id: 'valid-id',
          itemId: 'item-1',
          command: 'receipts',
          body: {},
          createdAt: 'now',
          attempts: 0,
        },
        { id: 'bad', command: 'delete-everything' },
      ]),
    )
    expect(readQueue()).toHaveLength(1)
  })
})
