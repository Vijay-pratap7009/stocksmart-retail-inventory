/** Day 5 KPI dashboard models mirroring the backend DTOs. */

import { PurchaseOrderStatus, SalesOrderStatus } from './order.models';

export interface DashboardSummary {
  warehouseId?: number | null;
  totalProducts: number;
  activeProducts: number;
  totalWarehouses: number;
  activeWarehouses: number;
  totalInventoryUnits: number;
  inventoryValue: number;
  lowStockCount: number;
  stockoutCount: number;
  generatedAt: string;
}

export interface LowStockItem {
  inventoryId: number;
  productId: number;
  sku: string;
  productName: string;
  unit: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  quantityOnHand: number;
  minimumStockLevel: number;
  deficit: number;
}

export interface StockoutItem {
  inventoryId: number;
  productId: number;
  sku: string;
  productName: string;
  unit: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  quantityOnHand: number;
  updatedAt?: string;
}

export interface PurchaseOrderSummary {
  warehouseId?: number | null;
  byStatus: Partial<Record<PurchaseOrderStatus, number>>;
  totalOrders: number;
  pendingCount: number;
  generatedAt: string;
}

export interface SalesFulfillmentSummary {
  warehouseId?: number | null;
  byStatus: Partial<Record<SalesOrderStatus, number>>;
  totalOrders: number;
  pendingCount: number;
  remainingToFulfillUnits: number;
  generatedAt: string;
}
