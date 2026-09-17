export interface StoreComparisonRow {
  storeId: number;
  storeName: string;
  storeCode: string;
  totalSales: number;
  salesCount: number;
  totalPurchases: number;
  purchaseCount: number;
  totalStockUnits: number;
  lowStockCount: number;
  outOfStockCount: number;
}

export interface StoreComparisonResponse {
  fromDate: string;
  toDate: string;
  rows: StoreComparisonRow[];
}
