import api from './axios';
import { PagedResponse } from '../types/user';
import {
  Account,
  AccountGroup,
  AccountLedgerResponse,
  AccountPayload,
  AccountType,
  AccountingDashboardSummary,
  DayBookResponse,
  JournalCreatePayload,
  JournalHeader,
  TrialBalanceResponse,
  VoucherType,
} from '../types/accounting';

export interface AccountQuery {
  search?: string;
  accountType?: AccountType | '';
  active?: boolean;
  page?: number;
  size?: number;
}

export const accountApi = {
  list: (query: AccountQuery) =>
    api.get<PagedResponse<Account>>('/accounts', {
      params: {
        search: query.search || undefined,
        accountType: query.accountType || undefined,
        active: query.active,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    }),
  groups: () => api.get<AccountGroup[]>('/accounts/groups'),
  getById: (id: number) => api.get<Account>(`/accounts/${id}`),
  create: (payload: AccountPayload) => api.post<Account>('/accounts', payload),
  update: (id: number, payload: AccountPayload) => api.put<Account>(`/accounts/${id}`, payload),
};

export interface JournalQuery {
  voucherType?: VoucherType | '';
  fromDate?: string;
  toDate?: string;
  search?: string;
  page?: number;
  size?: number;
}

export const journalApi = {
  list: (query: JournalQuery) =>
    api.get<PagedResponse<JournalHeader>>('/accounting/journals', {
      params: {
        voucherType: query.voucherType || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        search: query.search || undefined,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    }),
  getById: (id: number) => api.get<JournalHeader>(`/accounting/journals/${id}`),
  create: (payload: JournalCreatePayload) => api.post<JournalHeader>('/accounting/journals', payload),
};

export const accountingReportApi = {
  dashboard: () => api.get<AccountingDashboardSummary>('/accounting/reports/dashboard'),
  dayBook: (fromDate?: string, toDate?: string) =>
    api.get<DayBookResponse>('/accounting/reports/day-book', { params: { fromDate, toDate } }),
  accountLedger: (accountId: number, fromDate?: string, toDate?: string) =>
    api.get<AccountLedgerResponse>(`/accounting/reports/account-ledger/${accountId}`, { params: { fromDate, toDate } }),
  trialBalance: (asOfDate?: string) =>
    api.get<TrialBalanceResponse>('/accounting/reports/trial-balance', { params: { asOfDate } }),
};
