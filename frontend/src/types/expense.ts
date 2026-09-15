import { PaymentMode, TaxMode } from './sale';

export type ExpenseStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface Expense {
  id: number;
  expenseNumber: string;
  expenseDate: string;
  category: string;
  vendorName?: string;
  paymentMode: PaymentMode;
  taxMode?: TaxMode | null;
  gstPercent?: number;
  itcEligible: boolean;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalAmount: number;
  description?: string;
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
  category: string;
  vendorName?: string;
  paymentMode: PaymentMode;
  taxMode?: TaxMode | '';
  gstPercent?: number;
  itcEligible: boolean;
  taxableAmount: number;
  description?: string;
  post?: boolean;
}
