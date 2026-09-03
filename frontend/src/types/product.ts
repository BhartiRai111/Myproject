export type ProductStatus = 'ACTIVE' | 'INACTIVE';

export type CategoryStatus = 'ACTIVE' | 'INACTIVE';

export interface Category {
  id: number;
  name: string;
  description?: string;
  status: CategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryPayload {
  name: string;
  description?: string;
}

export interface Product {
  id: number;
  name: string;
  sku: string;
  barcode?: string;
  categoryId: number;
  categoryName: string;
  brand?: string;
  unit: string;
  purchasePrice: number;
  sellingPrice: number;
  tax: number;
  minStockLevel: number;
  stockQuantity: number;
  status: ProductStatus;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPayload {
  name: string;
  sku: string;
  barcode?: string;
  categoryId: number;
  brand?: string;
  unit?: string;
  purchasePrice: number;
  sellingPrice: number;
  tax?: number;
  minStockLevel?: number;
  description?: string;
}
