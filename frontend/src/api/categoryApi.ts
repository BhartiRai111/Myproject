import api from './axios';
import { Category, CategoryPayload } from '../types/product';

export const categoryApi = {
  list: () => api.get<Category[]>('/categories'),
  getById: (id: number) => api.get<Category>(`/categories/${id}`),
  create: (payload: CategoryPayload) => api.post<Category>('/categories', payload),
  update: (id: number, payload: CategoryPayload) => api.put<Category>(`/categories/${id}`, payload),
  activate: (id: number) => api.patch<Category>(`/categories/${id}/activate`),
  deactivate: (id: number) => api.patch<Category>(`/categories/${id}/deactivate`),
};
