import api from './axios';
import { PagedResponse } from '../types/user';
import { Expense, ExpenseCreatePayload, ExpenseStatus, ExpenseSummaryReportResponse, ExpenseUpdatePayload } from '../types/expense';
import { PaymentMode } from '../types/sale';

export interface ExpenseQuery {
  search?: string;
  status?: ExpenseStatus | '';
  category?: string;
  categoryId?: number;
  supplierId?: number;
  paymentMode?: PaymentMode | '';
  gstApplicable?: boolean;
  itcEligible?: boolean;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const expenseApi = {
  list: (query: ExpenseQuery) =>
    api.get<PagedResponse<Expense>>('/expenses', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        category: query.category || undefined,
        categoryId: query.categoryId || undefined,
        supplierId: query.supplierId || undefined,
        paymentMode: query.paymentMode || undefined,
        gstApplicable: query.gstApplicable,
        itcEligible: query.itcEligible,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Expense>(`/expenses/${id}`),
  create: (payload: ExpenseCreatePayload) => api.post<Expense>('/expenses', payload),
  update: (id: number, payload: ExpenseUpdatePayload) => api.put<Expense>(`/expenses/${id}`, payload),
  post: (id: number) => api.post<Expense>(`/expenses/${id}/post`),
  cancel: (id: number) => api.patch<Expense>(`/expenses/${id}/cancel`),
  summary: (fromDate?: string, toDate?: string) =>
    api.get<ExpenseSummaryReportResponse>('/expenses/reports/summary', { params: { fromDate, toDate } }),
};
