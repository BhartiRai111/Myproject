import api from './axios';
import { PagedResponse } from '../types/user';
import {
  Inventory,
  InventorySummary,
  ReferenceType,
  StockAdjustmentPayload,
  StockHistory,
  StockMovementType,
  StockStatus,
} from '../types/inventory';

export interface InventoryQuery {
  search?: string;
  categoryId?: number | '';
  stockStatus?: StockStatus | '';
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export interface StockHistoryQuery {
  productId?: number;
  movementType?: StockMovementType | '';
  referenceType?: ReferenceType | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const inventoryApi = {
  list: (query: InventoryQuery = {}) =>
    api.get<PagedResponse<Inventory>>('/inventory', {
      params: {
        search: query.search || undefined,
        categoryId: query.categoryId || undefined,
        stockStatus: query.stockStatus || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
        sortBy: query.sortBy || undefined,
        sortDir: query.sortDir || undefined,
      },
    }),
  getById: (id: number) => api.get<Inventory>(`/inventory/${id}`),
  getSummary: () => api.get<InventorySummary>('/inventory/summary'),
  adjust: (payload: StockAdjustmentPayload) => api.post<Inventory>('/inventory/adjust', payload),
  getHistoryForInventory: (id: number, page = 0, size = 10) =>
    api.get<PagedResponse<StockHistory>>(`/inventory/${id}/history`, { params: { page, size } }),
  getHistory: (query: StockHistoryQuery = {}) =>
    api.get<PagedResponse<StockHistory>>('/inventory/history', {
      params: {
        productId: query.productId || undefined,
        movementType: query.movementType || undefined,
        referenceType: query.referenceType || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
};
