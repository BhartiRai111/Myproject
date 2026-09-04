export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export type StockMovementType =
  | 'PURCHASE'
  | 'SALE'
  | 'STOCK_IN'
  | 'STOCK_OUT'
  | 'ADJUSTMENT'
  | 'SALE_CANCEL'
  | 'PURCHASE_CANCEL';

export type ManualAdjustmentType = 'STOCK_IN' | 'STOCK_OUT' | 'ADJUSTMENT';

export type ReferenceType = 'PURCHASE' | 'SALE' | 'MANUAL';

export interface Inventory {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  categoryId?: number;
  categoryName?: string;
  unit: string;
  currentStock: number;
  minStockLevel: number;
  maxStockLevel?: number;
  stockStatus: StockStatus;
  lastUpdated: string;
  createdAt: string;
  updatedAt: string;
}

export interface StockHistory {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  movementType: StockMovementType;
  quantity: number;
  previousStock: number;
  newStock: number;
  reason?: string;
  referenceType?: ReferenceType;
  referenceId?: number;
  notes?: string;
  createdAt: string;
  createdBy?: string;
}

export interface StockAdjustmentPayload {
  productId: number;
  movementType: ManualAdjustmentType;
  quantity: number;
  reason: string;
  notes?: string;
}

export interface InventorySummary {
  totalProducts: number;
  totalStockUnits: number;
  lowStockCount: number;
  outOfStockCount: number;
}
