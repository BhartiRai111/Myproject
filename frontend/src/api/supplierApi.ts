import api from './axios';
import { Supplier, SupplierCreatePayload } from '../types/purchase';

export const supplierApi = {
  list: () => api.get<Supplier[]>('/suppliers'),
  create: (payload: SupplierCreatePayload) => api.post<Supplier>('/suppliers', payload),
};
