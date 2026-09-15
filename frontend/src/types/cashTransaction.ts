import { PaymentMode } from './sale';

export type CashTransactionType = 'CASH_IN' | 'CASH_OUT';
export type CashTransactionStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface CashTransaction {
  id: number;
  transactionNumber: string;
  transactionDate: string;
  transactionType: CashTransactionType;
  paymentMode: PaymentMode;
  amount: number;
  reason: string;
  status: CashTransactionStatus;
  createdBy?: string;
  postedBy?: string;
  postedAt?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  createdAt: string;
}

export interface CashTransactionCreatePayload {
  transactionDate: string;
  transactionType: CashTransactionType;
  paymentMode: PaymentMode;
  amount: number;
  reason: string;
  post?: boolean;
}
