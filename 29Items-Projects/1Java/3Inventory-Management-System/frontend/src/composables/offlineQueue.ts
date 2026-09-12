import { apiRequest, ApiError, newIdempotencyKey } from '../api/client'
import type { InventoryItem } from '../types/inventory'

export interface QueuedCommand {
  id: string
  itemId: string
  command: 'adjustments' | 'receipts' | 'shipments'
  body: Record<string, unknown>
  createdAt: string
  attempts: number
}

const storageKey = 'inventory.offlineCommands'
const commands = new Set<QueuedCommand['command']>(['adjustments', 'receipts', 'shipments'])

function isQueuedCommand(value: unknown): value is QueuedCommand {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<QueuedCommand>
  return (
    typeof candidate.id === 'string' &&
    candidate.id.length >= 8 &&
    typeof candidate.itemId === 'string' &&
    candidate.itemId.length > 0 &&
    commands.has(candidate.command as QueuedCommand['command']) &&
    Boolean(candidate.body) &&
    typeof candidate.body === 'object' &&
    typeof candidate.createdAt === 'string' &&
    Number.isInteger(candidate.attempts) &&
    (candidate.attempts ?? -1) >= 0
  )
}

export function readQueue(): QueuedCommand[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey) ?? '[]') as unknown
    if (!Array.isArray(parsed)) throw new Error('Offline queue must be an array.')
    const valid = parsed.filter(isQueuedCommand).slice(-500)
    if (valid.length !== parsed.length) writeQueue(valid)
    return valid
  } catch {
    localStorage.removeItem(storageKey)
    return []
  }
}

function writeQueue(commands: QueuedCommand[]): void {
  localStorage.setItem(storageKey, JSON.stringify(commands))
  window.dispatchEvent(new CustomEvent('inventory-queue-changed'))
}

export function enqueueCommand(
  itemId: string,
  command: QueuedCommand['command'],
  body: Record<string, unknown>,
): QueuedCommand {
  const queued = {
    id: newIdempotencyKey(),
    itemId,
    command,
    body,
    createdAt: new Date().toISOString(),
    attempts: 0,
  }
  writeQueue([...readQueue(), queued])
  return queued
}

export async function flushQueue(): Promise<{ completed: number; remaining: number }> {
  const pending = readQueue()
  const remaining: QueuedCommand[] = []
  let completed = 0
  let processed = 0
  for (const command of pending) {
    try {
      await apiRequest<InventoryItem>(`/api/v1/inventory/${command.itemId}/${command.command}`, {
        method: 'POST',
        body: JSON.stringify(command.body),
        idempotencyKey: command.id,
      })
      completed++
      processed++
    } catch (error) {
      if (error instanceof ApiError && error.status > 0 && error.status < 500) {
        processed++
        continue
      }
      remaining.push({ ...command, attempts: command.attempts + 1 })
      processed++
      break
    }
  }
  const notAttempted = pending.slice(processed)
  writeQueue([...remaining, ...notAttempted])
  return { completed, remaining: remaining.length + notAttempted.length }
}
