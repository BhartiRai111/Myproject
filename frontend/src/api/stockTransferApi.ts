import api from './axios';
import { PagedResponse } from '../types/user';
import { StockTransfer, StockTransferPayload, StockTransferStatus } from '../types/stockTransfer';

export interface StockTransferListQuery {
  search?: string;
  status?: StockTransferStatus | '';
  fromDate?: string;
  toDate?: string;
  storeId?: number | '';
  page?: number;
  size?: number;
}

export const stockTransferApi = {
  list: (query: StockTransferListQuery = {}) =>
    api.get<PagedResponse<StockTransfer>>('/stock-transfers', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        storeId: query.storeId || undefined,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    }),
  getById: (id: number) => api.get<StockTransfer>(`/stock-transfers/${id}`),
  create: (payload: StockTransferPayload) => api.post<StockTransfer>('/stock-transfers', payload),
  approve: (id: number) => api.patch<StockTransfer>(`/stock-transfers/${id}/approve`),
  dispatch: (id: number) => api.patch<StockTransfer>(`/stock-transfers/${id}/dispatch`),
  receive: (id: number) => api.patch<StockTransfer>(`/stock-transfers/${id}/receive`),
  cancel: (id: number) => api.patch<StockTransfer>(`/stock-transfers/${id}/cancel`),
};
