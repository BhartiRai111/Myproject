import api from './axios';
import { FinancialYear, FinancialYearCreatePayload, FinancialYearSummary } from '../types/financialYear';

export const financialYearApi = {
  list: () => api.get<FinancialYear[]>('/financial-years'),
  getById: (id: number) => api.get<FinancialYear>(`/financial-years/${id}`),
  getCurrent: () => api.get<FinancialYear>('/financial-years/current'),
  getSummary: (id: number) => api.get<FinancialYearSummary>(`/financial-years/${id}/summary`),
  create: (payload: FinancialYearCreatePayload) => api.post<FinancialYear>('/financial-years', payload),
  open: (id: number) => api.patch<FinancialYear>(`/financial-years/${id}/open`),
  close: (id: number) => api.patch<FinancialYear>(`/financial-years/${id}/close`),
  markCurrent: (id: number) => api.patch<FinancialYear>(`/financial-years/${id}/mark-current`),
};
