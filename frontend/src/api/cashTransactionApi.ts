import api from './axios';
import { PagedResponse } from '../types/user';
import { CashTransaction, CashTransactionCreatePayload, CashTransactionStatus, CashTransactionType } from '../types/cashTransaction';

export interface CashTransactionQuery {
  search?: string;
  transactionType?: CashTransactionType | '';
  status?: CashTransactionStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const cashTransactionApi = {
  list: (query: CashTransactionQuery) =>
    api.get<PagedResponse<CashTransaction>>('/cash-transactions', {
      params: {
        search: query.search || undefined,
        transactionType: query.transactionType || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<CashTransaction>(`/cash-transactions/${id}`),
  create: (payload: CashTransactionCreatePayload) => api.post<CashTransaction>('/cash-transactions', payload),
  post: (id: number) => api.post<CashTransaction>(`/cash-transactions/${id}/post`),
  cancel: (id: number) => api.patch<CashTransaction>(`/cash-transactions/${id}/cancel`),
};
