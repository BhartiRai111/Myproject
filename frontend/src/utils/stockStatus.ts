import { Product } from '../types/product';

export type StockStatus = 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK';

export function getStockStatus(product: Pick<Product, 'stockQuantity' | 'minStockLevel'>): StockStatus {
  if (product.stockQuantity <= 0) return 'OUT_OF_STOCK';
  if (product.stockQuantity <= product.minStockLevel) return 'LOW_STOCK';
  return 'IN_STOCK';
}

export function stockStatusLabel(status: StockStatus): string {
  if (status === 'OUT_OF_STOCK') return 'Out of Stock';
  if (status === 'LOW_STOCK') return 'Low Stock';
  return 'In Stock';
}

export function stockStatusVariant(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'destructive' as const;
  if (status === 'LOW_STOCK') return 'warning' as const;
  return 'success' as const;
}
