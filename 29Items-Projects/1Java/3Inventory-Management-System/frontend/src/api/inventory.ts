import { apiRequest, newIdempotencyKey } from './client'
import type {
  Forecast,
  InventoryInput,
  InventoryItem,
  LowStockAlert,
  Page,
  Reservation,
  StockMovement,
  Warehouse,
} from '../types/inventory'

export function fetchInventory(
  filters: {
    warehouseId?: string
    query?: string
    active?: boolean
    page?: number
    size?: number
  } = {},
): Promise<Page<InventoryItem>> {
  const parameters = new URLSearchParams()
  if (filters.warehouseId) parameters.set('warehouseId', filters.warehouseId)
  if (filters.query) parameters.set('query', filters.query)
  if (filters.active !== undefined) parameters.set('active', String(filters.active))
  parameters.set('page', String(filters.page ?? 0))
  parameters.set('size', String(filters.size ?? 50))
  return apiRequest(`/api/v1/inventory?${parameters}`)
}

export function fetchInventoryByBarcode(
  barcode: string,
  warehouseId?: string,
): Promise<InventoryItem> {
  const suffix = warehouseId ? `?warehouseId=${encodeURIComponent(warehouseId)}` : ''
  return apiRequest(`/api/v1/inventory/barcode/${encodeURIComponent(barcode)}${suffix}`)
}

export function createInventory(input: InventoryInput): Promise<InventoryItem> {
  return apiRequest('/api/v1/inventory', {
    method: 'POST',
    body: JSON.stringify(input),
    idempotencyKey: newIdempotencyKey(),
  })
}

export function updateInventory(id: string, input: InventoryInput): Promise<InventoryItem> {
  return apiRequest(`/api/v1/inventory/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
    idempotencyKey: newIdempotencyKey(),
  })
}

export function deactivateInventory(item: InventoryItem, reason: string): Promise<void> {
  return apiRequest(`/api/v1/inventory/${item.id}`, {
    method: 'DELETE',
    body: JSON.stringify({ version: item.version, reason }),
  })
}

export function stockCommand(
  itemId: string,
  command: 'adjustments' | 'receipts' | 'shipments',
  body: object,
  idempotencyKey = newIdempotencyKey(),
): Promise<InventoryItem> {
  return apiRequest(`/api/v1/inventory/${itemId}/${command}`, {
    method: 'POST',
    body: JSON.stringify(body),
    idempotencyKey,
  })
}

export function createReservation(
  itemId: string,
  body: { quantity: number; externalReference: string; reason: string },
): Promise<Reservation> {
  return apiRequest(`/api/v1/inventory/${itemId}/reservations`, {
    method: 'POST',
    body: JSON.stringify(body),
    idempotencyKey: newIdempotencyKey(),
  })
}

export function completeReservation(
  id: string,
  action: 'release' | 'fulfill',
): Promise<Reservation> {
  return apiRequest(`/api/v1/reservations/${id}/${action}`, {
    method: 'POST',
    idempotencyKey: newIdempotencyKey(),
  })
}

export function transferStock(body: {
  sourceItemId: string
  destinationWarehouseId: string
  quantity: number
  reason: string
  reference?: string
}): Promise<object> {
  return apiRequest('/api/v1/transfers', {
    method: 'POST',
    body: JSON.stringify(body),
    idempotencyKey: newIdempotencyKey(),
  })
}

export function fetchMovements(itemId: string): Promise<Page<StockMovement>> {
  return apiRequest(`/api/v1/inventory/${itemId}/movements?size=100`)
}

export function fetchReservations(itemId: string): Promise<Page<Reservation>> {
  return apiRequest(`/api/v1/inventory/${itemId}/reservations?size=100`)
}

export function fetchForecast(itemId: string): Promise<Forecast> {
  return apiRequest(`/api/v1/inventory/${itemId}/forecast`)
}

export function refreshForecast(itemId: string): Promise<Forecast> {
  return apiRequest(`/api/v1/inventory/${itemId}/forecast/refresh`, { method: 'POST' })
}

export function fetchWarehouses(): Promise<Page<Warehouse>> {
  return apiRequest('/api/v1/warehouses?size=100')
}

export function createWarehouse(body: { code: string; name: string }): Promise<Warehouse> {
  return apiRequest('/api/v1/warehouses', { method: 'POST', body: JSON.stringify(body) })
}

export function updateWarehouse(warehouse: Warehouse): Promise<Warehouse> {
  return apiRequest(`/api/v1/warehouses/${warehouse.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      name: warehouse.name,
      active: warehouse.active,
      version: warehouse.version,
    }),
  })
}

export function fetchAlerts(): Promise<Page<LowStockAlert>> {
  return apiRequest('/api/v1/alerts?status=OPEN&size=100')
}

export function acknowledgeAlert(id: string): Promise<LowStockAlert> {
  return apiRequest(`/api/v1/alerts/${id}/acknowledge`, { method: 'POST' })
}
