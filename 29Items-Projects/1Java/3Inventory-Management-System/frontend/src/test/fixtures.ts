import type { InventoryItem, Warehouse } from '../types/inventory'

export const warehouse: Warehouse = {
  id: '10000000-0000-4000-8000-000000000001',
  code: 'MAIN',
  name: 'Main warehouse',
  active: true,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export const item: InventoryItem = {
  id: '20000000-0000-4000-8000-000000000001',
  sku: 'WIDGET-1',
  barcode: '4006381333931',
  barcodes: [{ barcode: '4006381333931', symbology: 'EAN_13', primary: true }],
  name: 'Widget',
  quantity: 12,
  reservedQuantity: 2,
  availableQuantity: 10,
  reorderPoint: 4,
  lowStock: false,
  active: true,
  warehouseId: warehouse.id,
  warehouseCode: warehouse.code,
  version: 1,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}
