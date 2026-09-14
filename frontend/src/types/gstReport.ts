import { AccountingPartyType, VoucherType } from './accounting';

export interface GstTransactionRow {
  gstTransactionId: number;
  sourceTransactionType: VoucherType;
  sourceTransactionId: number;
  voucherNumber: string | null;
  voucherDate: string;
  partyType: AccountingPartyType | null;
  partyId: number | null;
  partyName: string | null;
  partyGstin: string | null;
  placeOfSupplyStateCode: string | null;
  b2b: boolean;
  itcEligible: boolean | null;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  totalValue: number;
  returnPeriod: string;
}

export interface GstSummaryTotals {
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  totalValue: number;
  transactionCount: number;
}

export interface HsnSummaryRow {
  hsnCode: string;
  description: string;
  unit: string;
  totalQuantity: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalValue: number;
}

export interface TaxRateSummaryRow {
  gstPercent: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalValue: number;
}

export interface Gstr1Response {
  returnPeriod: string | null;
  fromDate: string | null;
  toDate: string | null;
  b2bTransactions: GstTransactionRow[];
  b2cTransactions: GstTransactionRow[];
  creditNotes: GstTransactionRow[];
  debitNotes: GstTransactionRow[];
  hsnSummary: HsnSummaryRow[];
  totals: GstSummaryTotals;
}

export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PurchaseGstReportResponse {
  returnPeriod: string | null;
  fromDate: string | null;
  toDate: string | null;
  transactions: PagedResult<GstTransactionRow>;
  totals: GstSummaryTotals;
  eligibleItcTotal: number;
}

export interface Gstr3bResponse {
  returnPeriod: string;
  outwardSupplies: GstSummaryTotals;
  inputTaxCredit: GstSummaryTotals;
  netLiability: GstSummaryTotals;
  note: string;
}

export interface GstReportListResponse {
  fromDate: string | null;
  toDate: string | null;
  transactions: PagedResult<GstTransactionRow>;
  totals: GstSummaryTotals;
}

export interface HsnSummaryReportResponse {
  fromDate: string | null;
  toDate: string | null;
  outward: HsnSummaryRow[];
  inward: HsnSummaryRow[];
}

export interface TaxRateSummaryReportResponse {
  fromDate: string | null;
  toDate: string | null;
  outward: TaxRateSummaryRow[];
  inward: TaxRateSummaryRow[];
}

export interface GstLiabilityResponse {
  returnPeriod: string;
  outputCgst: number;
  outputSgst: number;
  outputIgst: number;
  outputTotal: number;
  inputCgst: number;
  inputSgst: number;
  inputIgst: number;
  inputTotal: number;
  netCgst: number;
  netSgst: number;
  netIgst: number;
  netTotal: number;
}

export type ReconciliationStatus = 'MATCHED' | 'MISMATCHED' | 'MISSING' | 'DUPLICATE';

export interface ReconciliationRow {
  sourceTransactionType: VoucherType;
  sourceTransactionId: number;
  voucherNumber: string | null;
  voucherDate: string;
  status: ReconciliationStatus;
  sourceTaxableAmount: number;
  reportedTaxableAmount: number | null;
  sourceTotalTax: number;
  reportedTotalTax: number | null;
  remarks: string | null;
}

export interface ReconciliationResponse {
  fromDate: string | null;
  toDate: string | null;
  rows: ReconciliationRow[];
  matchedCount: number;
  mismatchedCount: number;
  missingCount: number;
  duplicateCount: number;
}
