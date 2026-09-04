import api from './axios';
import { PagedResponse } from '../types/user';
import { SalesOrder, SalesOrderPayload, SalesOrderStatus } from '../types/salesOrder';

export interface SalesOrderQuery {
  search?: string;
  customerId?: number | '';
  status?: SalesOrderStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const salesOrderApi = {
  list: (query: SalesOrderQuery = {}) =>
    api.get<PagedResponse<SalesOrder>>('/sales-orders', {
      params: {
        search: query.search || undefined,
        customerId: query.customerId || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<SalesOrder>(`/sales-orders/${id}`),
  create: (payload: SalesOrderPayload) => api.post<SalesOrder>('/sales-orders', payload),
  update: (id: number, payload: SalesOrderPayload) => api.put<SalesOrder>(`/sales-orders/${id}`, payload),
  setStatus: (id: number, status: SalesOrderStatus) =>
    api.patch<SalesOrder>(`/sales-orders/${id}/status`, null, { params: { status } }),
  cancel: (id: number) => api.patch<SalesOrder>(`/sales-orders/${id}/cancel`),
};
