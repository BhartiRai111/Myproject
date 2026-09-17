import api from './axios';
import { PagedResponse } from '../types/user';
import { Store, StoreGstContext, StorePayload } from '../types/store';
import { MasterListQuery } from './mastersApi';

export const storeApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Store>>('/masters/stores', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Store>(`/masters/stores/${id}`),
  generateCode: () => api.post<{ storeCode: string }>('/masters/stores/generate-code'),
  create: (payload: StorePayload) => api.post<Store>('/masters/stores', payload),
  update: (id: number, payload: StorePayload) => api.put<Store>(`/masters/stores/${id}`, payload),
  activate: (id: number) => api.patch<Store>(`/masters/stores/${id}/activate`),
  deactivate: (id: number) => api.patch<Store>(`/masters/stores/${id}/deactivate`),
  gstContext: (id: number) => api.get<StoreGstContext>(`/masters/stores/${id}/gst-context`),
};
