import api from './axios';
import { PagedResponse } from '../types/user';
import { CustomerOutstanding, Receipt, ReceiptPayload } from '../types/receipt';

export interface ReceiptQuery {
  search?: string;
  customerId?: number | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const receiptApi = {
  list: (query: ReceiptQuery = {}) =>
    api.get<PagedResponse<Receipt>>('/receipts', {
      params: {
        search: query.search || undefined,
        customerId: query.customerId || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Receipt>(`/receipts/${id}`),
  getOutstanding: (customerId: number) => api.get<CustomerOutstanding>(`/receipts/outstanding/${customerId}`),
  create: (payload: ReceiptPayload) => api.post<Receipt>('/receipts', payload),
  remove: (id: number) => api.delete(`/receipts/${id}`),
};
