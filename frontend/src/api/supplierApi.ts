import api from './axios';
import { PagedResponse } from '../types/user';
import { Supplier, SupplierPayload, SupplierPurchaseSummary, SupplierStatus } from '../types/supplier';

export interface SupplierQuery {
  search?: string;
  status?: SupplierStatus | '';
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export const supplierApi = {
  list: (query: SupplierQuery = {}) =>
    api.get<PagedResponse<Supplier>>('/suppliers', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
        sortBy: query.sortBy || undefined,
        sortDir: query.sortDir || undefined,
      },
    }),
  getById: (id: number) => api.get<Supplier>(`/suppliers/${id}`),
  getPurchaseHistory: (id: number) => api.get<SupplierPurchaseSummary[]>(`/suppliers/${id}/purchases`),
  create: (payload: SupplierPayload) => api.post<Supplier>('/suppliers', payload),
  update: (id: number, payload: SupplierPayload) => api.put<Supplier>(`/suppliers/${id}`, payload),
  activate: (id: number) => api.patch<Supplier>(`/suppliers/${id}/activate`),
  deactivate: (id: number) => api.patch<Supplier>(`/suppliers/${id}/deactivate`),
};
