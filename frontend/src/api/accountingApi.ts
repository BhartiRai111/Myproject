import api from './axios';
import { PagedResponse } from '../types/user';
import {
  Account,
  AccountGroup,
  AccountLedgerResponse,
  AccountPayload,
  AccountSummaryResponse,
  AccountType,
  AccountingDashboardSummary,
  AccountingHealthCheckResponse,
  AccountingPartyType,
  BalanceSheetResponse,
  CashBankBookResponse,
  DayBookResponse,
  ExpenseIncomeSummaryResponse,
  JournalCreatePayload,
  JournalHeader,
  JournalStatus,
  OutstandingBillReportResponse,
  PartyLedgerResponse,
  ProfitLossResponse,
  ReceivablePayableResponse,
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
  status?: JournalStatus | '';
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
        status: query.status || undefined,
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
  dayBook: (fromDate?: string, toDate?: string, voucherType?: VoucherType | '') =>
    api.get<DayBookResponse>('/accounting/reports/day-book', { params: { fromDate, toDate, voucherType: voucherType || undefined } }),
  accountLedger: (accountId: number, fromDate?: string, toDate?: string) =>
    api.get<AccountLedgerResponse>(`/accounting/reports/account-ledger/${accountId}`, { params: { fromDate, toDate } }),
  trialBalance: (asOfDate?: string) =>
    api.get<TrialBalanceResponse>('/accounting/reports/trial-balance', { params: { asOfDate } }),
  cashBook: (fromDate?: string, toDate?: string) =>
    api.get<CashBankBookResponse>('/accounting/reports/cash-book', { params: { fromDate, toDate } }),
  bankBook: (fromDate?: string, toDate?: string, accountId?: number) =>
    api.get<CashBankBookResponse>('/accounting/reports/bank-book', { params: { fromDate, toDate, accountId } }),
  partyLedger: (partyType: AccountingPartyType, partyId: number, fromDate?: string, toDate?: string) =>
    api.get<PartyLedgerResponse>('/accounting/reports/party-ledger', { params: { partyType, partyId, fromDate, toDate } }),
  receivable: (fromDate?: string, toDate?: string) =>
    api.get<ReceivablePayableResponse>('/accounting/reports/receivable', { params: { fromDate, toDate } }),
  payable: (fromDate?: string, toDate?: string) =>
    api.get<ReceivablePayableResponse>('/accounting/reports/payable', { params: { fromDate, toDate } }),
  outstandingCustomers: (asOfDate?: string) =>
    api.get<OutstandingBillReportResponse>('/accounting/reports/outstanding/customers', { params: { asOfDate } }),
  outstandingSuppliers: (asOfDate?: string) =>
    api.get<OutstandingBillReportResponse>('/accounting/reports/outstanding/suppliers', { params: { asOfDate } }),
  profitLoss: (fromDate?: string, toDate?: string) =>
    api.get<ProfitLossResponse>('/accounting/reports/profit-loss', { params: { fromDate, toDate } }),
  balanceSheet: (asOfDate?: string) =>
    api.get<BalanceSheetResponse>('/accounting/reports/balance-sheet', { params: { asOfDate } }),
  accountSummary: (accountType?: AccountType | '', fromDate?: string, toDate?: string) =>
    api.get<AccountSummaryResponse>('/accounting/reports/account-summary', { params: { accountType: accountType || undefined, fromDate, toDate } }),
  expenseSummary: (fromDate?: string, toDate?: string) =>
    api.get<ExpenseIncomeSummaryResponse>('/accounting/reports/expense-summary', { params: { fromDate, toDate } }),
  incomeSummary: (fromDate?: string, toDate?: string) =>
    api.get<ExpenseIncomeSummaryResponse>('/accounting/reports/income-summary', { params: { fromDate, toDate } }),
  healthCheck: () => api.get<AccountingHealthCheckResponse>('/accounting/reports/health-check'),
};
