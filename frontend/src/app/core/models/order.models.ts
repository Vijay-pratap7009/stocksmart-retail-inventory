/** Day 4 order models mirroring the backend DTOs. */

export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED';

export type SalesOrderStatus =
  | 'DRAFT'
  | 'CONFIRMED'
  | 'PARTIALLY_FULFILLED'
  | 'FULFILLED'
  | 'CANCELLED';

export interface OrderLineRequest {
  productId: number;
  orderedQuantity: number;
  unitCost?: number;
  unitPrice?: number;
}

export interface PurchaseOrderLine {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  orderedQuantity: number;
  receivedQuantity: number;
  remainingQuantity: number;
  unitCost: number;
  lineTotal: number;
}

export interface PurchaseOrder {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierName: string;
  supplierCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  orderDate: string;
  expectedDeliveryDate?: string | null;
  status: PurchaseOrderStatus;
  notes?: string | null;
  createdByUsername: string;
  items: PurchaseOrderLine[];
  orderTotal: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PurchaseOrderRequest {
  poNumber?: string | null;
  supplierId: number;
  warehouseId: number;
  orderDate: string;
  expectedDeliveryDate?: string | null;
  notes?: string | null;
  items: { productId: number; orderedQuantity: number; unitCost: number }[];
}

export interface ReceiptLine {
  id: number;
  poItemId: number;
  productId: number;
  sku: string;
  quantity: number;
}

export interface PurchaseReceipt {
  id: number;
  receiptNumber: string;
  purchaseOrderId: number;
  poNumber: string;
  receivedByUsername: string;
  receivedAt?: string;
  notes?: string | null;
  lines: ReceiptLine[];
}

export interface SalesOrderLine {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  orderedQuantity: number;
  fulfilledQuantity: number;
  remainingQuantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface SalesOrder {
  id: number;
  soNumber: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  customerName: string;
  customerContact?: string | null;
  orderDate: string;
  status: SalesOrderStatus;
  notes?: string | null;
  createdByUsername: string;
  items: SalesOrderLine[];
  orderTotal: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SalesOrderRequest {
  soNumber?: string | null;
  warehouseId: number;
  customerName: string;
  customerContact?: string | null;
  orderDate: string;
  notes?: string | null;
  items: { productId: number; orderedQuantity: number; unitPrice: number }[];
}

export interface FulfillmentLine {
  id: number;
  soItemId: number;
  productId: number;
  sku: string;
  quantity: number;
}

export interface SalesFulfillment {
  id: number;
  fulfillmentNumber: string;
  salesOrderId: number;
  soNumber: string;
  fulfilledByUsername: string;
  fulfilledAt?: string;
  notes?: string | null;
  lines: FulfillmentLine[];
}
