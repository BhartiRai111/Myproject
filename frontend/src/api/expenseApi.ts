import api from './axios';
import { PagedResponse } from '../types/user';
import { Expense, ExpenseCreatePayload, ExpenseStatus } from '../types/expense';

export interface ExpenseQuery {
  search?: string;
  status?: ExpenseStatus | '';
  category?: string;
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
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<Expense>(`/expenses/${id}`),
  create: (payload: ExpenseCreatePayload) => api.post<Expense>('/expenses', payload),
  post: (id: number) => api.post<Expense>(`/expenses/${id}/post`),
  cancel: (id: number) => api.patch<Expense>(`/expenses/${id}/cancel`),
};
