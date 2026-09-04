import api from './axios';
import { PagedResponse } from '../types/user';
import { PurchaseOrder, PurchaseOrderPayload, PurchaseOrderStatus } from '../types/purchaseOrder';

export interface PurchaseOrderQuery {
  search?: string;
  supplierId?: number | '';
  status?: PurchaseOrderStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const purchaseOrderApi = {
  list: (query: PurchaseOrderQuery = {}) =>
    api.get<PagedResponse<PurchaseOrder>>('/purchase-orders', {
      params: {
        search: query.search || undefined,
        supplierId: query.supplierId || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<PurchaseOrder>(`/purchase-orders/${id}`),
  create: (payload: PurchaseOrderPayload) => api.post<PurchaseOrder>('/purchase-orders', payload),
  update: (id: number, payload: PurchaseOrderPayload) => api.put<PurchaseOrder>(`/purchase-orders/${id}`, payload),
  setStatus: (id: number, status: PurchaseOrderStatus) =>
    api.patch<PurchaseOrder>(`/purchase-orders/${id}/status`, null, { params: { status } }),
  cancel: (id: number) => api.patch<PurchaseOrder>(`/purchase-orders/${id}/cancel`),
};
