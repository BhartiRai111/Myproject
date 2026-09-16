import api from './axios';
import { PagedResponse } from '../types/user';
import { ImportResult } from '../types/importResult';
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
  generateSku: () => api.post<{ sku: string }>('/products/generate-sku'),
  create: (payload: ProductPayload) => api.post<Product>('/products', payload),
  update: (id: number, payload: ProductPayload) => api.put<Product>(`/products/${id}`, payload),
  activate: (id: number) => api.patch<Product>(`/products/${id}/activate`),
  deactivate: (id: number) => api.patch<Product>(`/products/${id}/deactivate`),
  exportCsv: (query: ProductQuery = {}) =>
    api.get<Blob>('/products/export', {
      responseType: 'blob',
      params: {
        search: query.search || undefined,
        categoryId: query.categoryId || undefined,
        status: query.status || undefined,
      },
    }),
  importCsv: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<ImportResult>('/products/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};
