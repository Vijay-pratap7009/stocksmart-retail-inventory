/** Day 3 inventory models mirroring the backend DTOs. */

export type TransactionType =
  | 'OPENING_BALANCE'
  | 'RECEIPT'
  | 'ISSUE'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT';

export interface Inventory {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  unit: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  quantityOnHand: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface StockTransaction {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  type: TransactionType;
  quantity: number;
  quantityBefore: number;
  quantityAfter: number;
  referenceNumber: string;
  reason?: string | null;
  performedByUsername: string;
  notes?: string | null;
  createdAt?: string;
}

export interface ReceiptRequest {
  productId: number;
  warehouseId: number;
  quantity: number;
  notes?: string | null;
}

export interface AdjustmentRequest {
  productId: number;
  warehouseId: number;
  direction: 'IN' | 'OUT';
  quantity: number;
  reason: string;
  notes?: string | null;
}

export interface TransferRequest {
  productId: number;
  sourceWarehouseId: number;
  destinationWarehouseId: number;
  quantity: number;
  reference?: string | null;
  notes?: string | null;
}

export interface TransferResult {
  referenceNumber: string;
  transferOut: StockTransaction;
  transferIn: StockTransaction;
}
