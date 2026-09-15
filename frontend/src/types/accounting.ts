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
  status: JournalStatus;
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
  difference: number;
  balanced: boolean;
}

export interface AccountingDashboardSummary {
  cashBalance: number;
  bankBalance: number;
  receivableBalance: number;
  payableBalance: number;
}

// ---- Phase 4: Accounting Reports & Financial Statements ----

export interface CashBankBookRow {
  voucherDate: string;
  voucherType: VoucherType;
  voucherNumber: string | null;
  particulars: string | null;
  receipt: number | null;
  payment: number | null;
  runningBalance: number;
}

export interface CashBankBookResponse {
  accountId: number;
  accountCode: string;
  accountName: string;
  fromDate: string | null;
  toDate: string | null;
  openingBalance: number;
  rows: CashBankBookRow[];
  totalReceipts: number;
  totalPayments: number;
  closingBalance: number;
}

export interface PartyLedgerRow {
  voucherDate: string;
  voucherType: VoucherType;
  voucherNumber: string | null;
  particulars: string | null;
  debit: number;
  credit: number;
  balance: number;
  balanceType: LedgerEntryType;
}

export interface PartyLedgerResponse {
  partyType: AccountingPartyType;
  partyId: number;
  partyName: string;
  fromDate: string | null;
  toDate: string | null;
  openingBalance: number;
  openingBalanceType: LedgerEntryType;
  rows: PartyLedgerRow[];
  closingBalance: number;
  closingBalanceType: LedgerEntryType;
}

export interface ReceivablePayableRow {
  partyId: number;
  partyName: string;
  openingBalance: number;
  transactionAmount: number;
  paymentAmount: number;
  creditNoteAmount: number;
  debitNoteAmount: number;
  closingOutstanding: number;
}

export interface ReceivablePayableResponse {
  partyType: AccountingPartyType;
  fromDate: string | null;
  toDate: string | null;
  rows: ReceivablePayableRow[];
  totalOpening: number;
  totalTransactions: number;
  totalPayments: number;
  totalOutstanding: number;
}

export interface OutstandingBillDetailRow {
  partyId: number | null;
  partyName: string;
  billId: number;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceAmount: number;
  receivedOrPaidAmount: number;
  outstanding: number;
  daysOutstanding: number;
  ageingBucket: string;
}

export interface AgeingBucketSummary {
  bucket: string;
  count: number;
  amount: number;
}

export interface OutstandingBillReportResponse {
  partyType: AccountingPartyType;
  asOfDate: string;
  ageingBasis: string;
  rows: OutstandingBillDetailRow[];
  ageingSummary: AgeingBucketSummary[];
  totalInvoiceAmount: number;
  totalReceivedOrPaid: number;
  totalOutstanding: number;
}

export interface PnlAccountLine {
  accountId: number;
  accountCode: string;
  accountName: string;
  amount: number;
}

export interface ProfitLossResponse {
  fromDate: string | null;
  toDate: string | null;
  incomeLines: PnlAccountLine[];
  expenseLines: PnlAccountLine[];
  totalIncome: number;
  totalExpense: number;
  netProfitOrLoss: number;
}

export interface BalanceSheetLine {
  accountId: number | null;
  accountCode: string | null;
  accountName: string;
  amount: number;
}

export interface BalanceSheetResponse {
  asOfDate: string;
  assetLines: BalanceSheetLine[];
  liabilityLines: BalanceSheetLine[];
  equityLines: BalanceSheetLine[];
  totalAssets: number;
  totalLiabilities: number;
  totalEquity: number;
  currentYearProfit: number;
  currentFinancialYear: string;
  difference: number;
  balanced: boolean;
}

export interface AccountSummaryRow {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  openingBalance: number;
  debit: number;
  credit: number;
  closingBalance: number;
}

export interface AccountSummaryResponse {
  fromDate: string | null;
  toDate: string | null;
  rows: AccountSummaryRow[];
  totalOpening: number;
  totalDebit: number;
  totalCredit: number;
  totalClosing: number;
}

export interface ExpenseIncomeVoucherRow {
  voucherDate: string;
  journalId: number;
  journalNumber: string;
  voucherType: VoucherType;
  voucherNumber: string | null;
  narration: string | null;
  amount: number;
}

export interface ExpenseIncomeAccountGroup {
  accountId: number;
  accountCode: string;
  accountName: string;
  total: number;
  vouchers: ExpenseIncomeVoucherRow[];
}

export interface ExpenseIncomeSummaryResponse {
  fromDate: string | null;
  toDate: string | null;
  accounts: ExpenseIncomeAccountGroup[];
  totalAmount: number;
}

export type HealthCheckStatus = 'PASS' | 'WARNING' | 'ERROR';

export interface HealthCheckFinding {
  checkName: string;
  status: HealthCheckStatus;
  message: string;
  details: string[];
}

export interface AccountingHealthCheckResponse {
  generatedAt: string;
  overallStatus: HealthCheckStatus;
  findings: HealthCheckFinding[];
}
