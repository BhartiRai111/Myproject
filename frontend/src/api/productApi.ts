import api from './axios';
import { Product, ProductCreatePayload } from '../types/purchase';

export const productApi = {
  list: () => api.get<Product[]>('/products'),
  create: (payload: ProductCreatePayload) => api.post<Product>('/products', payload),
};
