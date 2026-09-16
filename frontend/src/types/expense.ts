import { PaymentMode, TaxMode } from './sale';

export type ExpenseStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';
export type ExpensePaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID';

export interface Expense {
  id: number;
  expenseNumber: string;
  expenseDate: string;
  category: string;
  categoryId?: number | null;
  vendorName?: string;
  supplierId?: number | null;
  supplierGstin?: string | null;
  paymentMode: PaymentMode;
  taxMode?: TaxMode | null;
  gstPercent?: number;
  itcEligible: boolean;
  discountAmount?: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalAmount: number;
  paidAmount: number;
  payableAmount: number;
  paymentStatus?: ExpensePaymentStatus | null;
  description?: string;
  referenceNumber?: string;
  remarks?: string;
  status: ExpenseStatus;
  createdBy?: string;
  postedBy?: string;
  postedAt?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseCreatePayload {
  expenseDate: string;
  categoryId?: number;
  category?: string;
  supplierId?: number;
  vendorName?: string;
  paymentMode: PaymentMode;
  taxMode?: TaxMode | '';
  gstPercent?: number;
  itcEligible: boolean;
  grossAmount?: number;
  discountAmount?: number;
  taxableAmount?: number;
  description?: string;
  referenceNumber?: string;
  remarks?: string;
  post?: boolean;
}

export type ExpenseUpdatePayload = ExpenseCreatePayload;

export interface ExpenseSummaryGroupRow {
  key: string;
  label: string;
  totalAmount: number;
  count: number;
}

export interface ExpenseSummaryReportResponse {
  fromDate?: string;
  toDate?: string;
  totalAmount: number;
  totalCount: number;
  byCategory: ExpenseSummaryGroupRow[];
  byPaymentMode: ExpenseSummaryGroupRow[];
  byParty: ExpenseSummaryGroupRow[];
  gstApplicableAmount: number;
  nonGstAmount: number;
  itcEligibleTax: number;
  itcIneligibleTax: number;
}
