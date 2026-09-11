export type AccountType = 'ASSET' | 'LIABILITY' | 'INCOME' | 'EXPENSE';

export type LedgerEntryType = 'DEBIT' | 'CREDIT';

export type VoucherType = 'SALE' | 'PURCHASE' | 'RECEIPT' | 'PAYMENT' | 'JOURNAL' | 'CREDIT_NOTE' | 'DEBIT_NOTE';

export type JournalStatus = 'DRAFT' | 'POSTED' | 'REVERSED';

export type AccountingPartyType = 'CUSTOMER' | 'SUPPLIER';

export interface AccountGroup {
  id: number;
  name: string;
  accountType: AccountType;
  parentGroupId: number | null;
  parentGroupName: string | null;
  active: boolean;
}

export interface Account {
  id: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  accountGroupId: number | null;
  accountGroupName: string | null;
  openingBalance: number;
  openingBalanceType: LedgerEntryType;
  systemAccount: boolean;
  active: boolean;
  createdBy: string | null;
  createdAt: string;
  modifiedBy: string | null;
  updatedAt: string;
}

export interface AccountPayload {
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  accountGroupId: number | null;
  openingBalance: number;
  openingBalanceType: LedgerEntryType;
  active: boolean;
}

export interface JournalLine {
  id: number;
  accountId: number;
  accountCode: string;
  accountName: string;
  debitAmount: number;
  creditAmount: number;
  narration: string | null;
  partyType: AccountingPartyType | null;
  partyId: number | null;
  referenceType: VoucherType | null;
  referenceId: number | null;
}

export interface JournalHeader {
  id: number;
  journalNumber: string;
  journalDate: string;
  voucherType: VoucherType;
  voucherId: number | null;
  voucherNumber: string | null;
  narration: string | null;
  status: JournalStatus;
  reversalOfJournalId: number | null;
  postedBy: string | null;
  postedAt: string | null;
  createdBy: string | null;
  createdAt: string;
  totalDebit: number;
  totalCredit: number;
  lines: JournalLine[];
}

export interface JournalLinePayload {
  accountId: number;
  debitAmount: number;
  creditAmount: number;
  narration?: string;
  partyType?: AccountingPartyType;
  partyId?: number;
}

export interface JournalCreatePayload {
  journalDate: string;
  narration?: string;
  lines: JournalLinePayload[];
}

export interface DayBookRow {
  journalDate: string;
  journalId: number;
  journalNumber: string;
  voucherType: VoucherType;
  voucherNumber: string | null;
  narration: string | null;
  debit: number;
  credit: number;
}

export interface DayBookResponse {
  rows: DayBookRow[];
  totalDebit: number;
  totalCredit: number;
}

export interface AccountLedgerRow {
  journalDate: string;
  voucherType: VoucherType;
  voucherNumber: string | null;
  particulars: string | null;
  debit: number;
  credit: number;
  balance: number;
  balanceType: LedgerEntryType;
}

export interface AccountLedgerResponse {
  accountId: number;
  accountCode: string;
  accountName: string;
  openingBalance: number;
  openingBalanceType: LedgerEntryType;
  rows: AccountLedgerRow[];
  closingBalance: number;
  closingBalanceType: LedgerEntryType;
}

export interface TrialBalanceRow {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  debit: number;
  credit: number;
}

export interface TrialBalanceResponse {
  asOfDate: string | null;
  rows: TrialBalanceRow[];
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
}

export interface AccountingDashboardSummary {
  cashBalance: number;
  bankBalance: number;
  receivableBalance: number;
  payableBalance: number;
}
