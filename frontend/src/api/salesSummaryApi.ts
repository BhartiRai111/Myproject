import api from './axios';
import { SalesSummary } from '../types/receipt';

export const salesSummaryApi = {
  get: () => api.get<SalesSummary>('/sales-summary'),
};
