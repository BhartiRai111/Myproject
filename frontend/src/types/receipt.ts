import { Customer, PaymentMode } from './sale';

export interface ReceiptAllocation {
  id: number;
  saleId: number;
  invoiceNumber: string;
  amountApplied: number;
}

export interface Receipt {
  id: number;
  receiptNumber: string;
  receiptDate: string;
  customer: Customer | null;
  amount: number;
  paymentMode: PaymentMode;
  remarks?: string;
  allocations: ReceiptAllocation[];
  createdAt: string;
  updatedAt: string;
}

export interface ReceiptAllocationPayload {
  saleId: number;
  amountApplied: number;
}

export interface ReceiptPayload {
  customerId: number;
  receiptDate: string;
  amount: number;
  paymentMode: PaymentMode;
  remarks?: string;
  allocations?: ReceiptAllocationPayload[];
}

export interface OutstandingBill {
  saleId: number;
  invoiceNumber: string;
  saleDate: string;
  totalAmount: number;
  paidAmount: number;
  dueAmount: number;
}

export interface CustomerOutstanding {
  customerId: number;
  totalOutstanding: number;
  bills: OutstandingBill[];
}

export interface SalesSummary {
  totalSalesOrders: number;
  pendingOrders: number;
  totalSalesBills: number;
  totalReceivables: number;
  todaysSales: number;
}
