import api from './axios';
import { PagedResponse } from '../types/user';
import { Product, ProductPayload, ProductStatus } from '../types/product';

export interface ProductQuery {
  search?: string;
  categoryId?: number | '';
  status?: ProductStatus | '';
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export const productApi = {
  list: (query: ProductQuery = {}) =>
    api.get<PagedResponse<Product>>('/products', {
      params: {
        search: query.search || undefined,
        categoryId: query.categoryId || undefined,
        status: query.status || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
        sortBy: query.sortBy || undefined,
        sortDir: query.sortDir || undefined,
      },
    }),
  getById: (id: number) => api.get<Product>(`/products/${id}`),
  create: (payload: ProductPayload) => api.post<Product>('/products', payload),
  update: (id: number, payload: ProductPayload) => api.put<Product>(`/products/${id}`, payload),
  activate: (id: number) => api.patch<Product>(`/products/${id}/activate`),
  deactivate: (id: number) => api.patch<Product>(`/products/${id}/deactivate`),
};
