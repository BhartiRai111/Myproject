import api from './axios';
import { Customer, CustomerCreatePayload } from '../types/sale';

export const customerApi = {
  list: () => api.get<Customer[]>('/customers'),
  create: (payload: CustomerCreatePayload) => api.post<Customer>('/customers', payload),
};
