import api from './axios';
import { PagedResponse } from '../types/user';
import { Payment, PaymentPayload, SupplierOutstanding } from '../types/payment';

export interface PaymentQuery {
  search?: string;
  supplierId?: number | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const paymentApi = {
  list: (query: PaymentQuery = {}) =>
    api.get<PagedResponse<Payment>>('/payments', {
      params: {
        search: query.search || undefined,
        supplierId: query.supplierId || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Payment>(`/payments/${id}`),
  getOutstanding: (supplierId: number) => api.get<SupplierOutstanding>(`/payments/outstanding/${supplierId}`),
  create: (payload: PaymentPayload) => api.post<Payment>('/payments', payload),
  remove: (id: number) => api.delete(`/payments/${id}`),
};
