import api from './axios';
import { PaymentMethod, PaymentMethodPayload } from '../types/paymentMethod';

export const paymentMethodApi = {
  listAll: () => api.get<PaymentMethod[]>('/payment-methods'),
  listActive: () => api.get<PaymentMethod[]>('/payment-methods', { params: { activeOnly: true } }),
  create: (payload: PaymentMethodPayload) => api.post<PaymentMethod>('/payment-methods', payload),
  update: (id: number, payload: PaymentMethodPayload) => api.put<PaymentMethod>(`/payment-methods/${id}`, payload),
  activate: (id: number) => api.patch<PaymentMethod>(`/payment-methods/${id}/activate`),
  deactivate: (id: number) => api.patch<PaymentMethod>(`/payment-methods/${id}/deactivate`),
};
