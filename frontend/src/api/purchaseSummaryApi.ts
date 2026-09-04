import api from './axios';
import { PurchaseSummary } from '../types/payment';

export const purchaseSummaryApi = {
  get: () => api.get<PurchaseSummary>('/purchase-summary'),
};
