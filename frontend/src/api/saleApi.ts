import api from './axios';
import { PagedResponse } from '../types/user';
import { PaymentStatus } from '../types/purchase';
import { Sale, SaleCreatePayload, SaleStatus, SaleTransactionType, SaleUpdatePayload } from '../types/sale';

export interface SaleQuery {
  search?: string;
  paymentStatus?: PaymentStatus | '';
  status?: SaleStatus | '';
  fromDate?: string;
  toDate?: string;
  transactionType?: SaleTransactionType | '';
  page?: number;
  size?: number;
}

export const saleApi = {
  list: (query: SaleQuery) =>
    api.get<PagedResponse<Sale>>('/sales', {
      params: {
        search: query.search || undefined,
        paymentStatus: query.paymentStatus || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        transactionType: query.transactionType || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Sale>(`/sales/${id}`),
  create: (payload: SaleCreatePayload) => api.post<Sale>('/sales', payload),
  update: (id: number, payload: SaleUpdatePayload) => api.put<Sale>(`/sales/${id}`, payload),
  post: (id: number) => api.post<Sale>(`/sales/${id}/post`),
  cancel: (id: number) => api.patch<Sale>(`/sales/${id}/cancel`),
  remove: (id: number) => api.delete(`/sales/${id}`),
};
