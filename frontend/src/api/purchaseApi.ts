import api from './axios';
import { PagedResponse } from '../types/user';
import {
  PaymentStatus,
  Purchase,
  PurchaseCreatePayload,
  PurchaseStatus,
  PurchaseUpdatePayload,
} from '../types/purchase';

export interface PurchaseQuery {
  search?: string;
  paymentStatus?: PaymentStatus | '';
  status?: PurchaseStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const purchaseApi = {
  list: (query: PurchaseQuery) =>
    api.get<PagedResponse<Purchase>>('/purchases', {
      params: {
        search: query.search || undefined,
        paymentStatus: query.paymentStatus || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Purchase>(`/purchases/${id}`),
  create: (payload: PurchaseCreatePayload) => api.post<Purchase>('/purchases', payload),
  update: (id: number, payload: PurchaseUpdatePayload) => api.put<Purchase>(`/purchases/${id}`, payload),
  cancel: (id: number) => api.patch<Purchase>(`/purchases/${id}/cancel`),
  remove: (id: number) => api.delete(`/purchases/${id}`),
};
