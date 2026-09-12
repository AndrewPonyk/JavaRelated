export type BarcodeSymbology =
  | 'EAN_8'
  | 'EAN_13'
  | 'UPC_A'
  | 'CODE_39'
  | 'CODE_128'
  | 'QR_CODE'
  | 'UNKNOWN'

export interface Barcode {
  id?: string
  barcode: string
  symbology: BarcodeSymbology
  primary: boolean
}

export interface InventoryItem {
  id: string
  sku: string
  barcode: string
  barcodes: Barcode[]
  name: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  reorderPoint: number
  lowStock: boolean
  active: boolean
  warehouseId: string
  warehouseCode: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface Warehouse {
  id: string
  code: string
  name: string
  active: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface InventoryInput {
  sku: string
  barcode: string
  symbology: BarcodeSymbology
  aliases: Barcode[]
  name: string
  reorderPoint: number
  warehouseId?: string
  quantity?: number
  active?: boolean
  version?: number
}

export interface StockMovement {
  id: string
  inventoryItemId: string
  relatedItemId?: string
  type: string
  quantityDelta: number
  quantityBefore: number
  quantityAfter: number
  reason: string
  referenceType?: string
  referenceId?: string
  actor: string
  occurredAt: string
}

export interface Reservation {
  id: string
  inventoryItemId: string
  quantity: number
  status: 'ACTIVE' | 'RELEASED' | 'FULFILLED'
  externalReference: string
  reason: string
  actor: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface Forecast {
  id: string
  inventoryItemId: string
  sku: string
  horizonDays: number
  predictedDemand: number
  lowerBound: number
  upperBound: number
  modelVersion: string
  featureVersion: string
  modelMae?: number
  generatedAt: string
}

export interface LowStockAlert {
  id: string
  eventId: string
  inventoryItemId: string
  sku: string
  itemName: string
  warehouseCode: string
  quantity: number
  reorderPoint: number
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  createdAt: string
  acknowledgedAt?: string
  acknowledgedBy?: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ProblemDetails {
  title?: string
  detail?: string
  code?: string
  errors?: Record<string, string>
}
