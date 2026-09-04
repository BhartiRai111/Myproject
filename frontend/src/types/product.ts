export type ProductStatus = 'ACTIVE' | 'INACTIVE';

export type CategoryStatus = 'ACTIVE' | 'INACTIVE';

export interface Category {
  id: number;
  name: string;
  description?: string;
  itemType?: string;
  applicableProperty?: string;
  status: CategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryPayload {
  name: string;
  description?: string;
  itemType?: string;
  applicableProperty?: string;
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
  manualCode?: string;
  itemGroupId?: number;
  itemGroupName?: string;
  hsnId?: number;
  hsnCode?: string;
  purchaseUnitId?: number;
  purchaseUnitName?: string;
  saleUnitId?: number;
  saleUnitName?: string;
  tolerancePercent?: number;
  itemType?: string;
  taxNature?: string;
  taxBasedOn?: string;
  partyName?: string;
  partyProductName?: string;
  freeValue?: number;
  applicableProperty?: string;
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
  manualCode?: string;
  itemGroupId?: number | '';
  hsnId?: number | '';
  purchaseUnitId?: number | '';
  saleUnitId?: number | '';
  tolerancePercent?: number;
  itemType?: string;
  taxNature?: string;
  taxBasedOn?: string;
  partyName?: string;
  partyProductName?: string;
  freeValue?: number;
  applicableProperty?: string;
}
