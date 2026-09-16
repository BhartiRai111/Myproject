import { Supplier } from './supplier';
import { PaymentMode } from './purchase';

export interface PaymentAllocation {
  id: number;
  purchaseId: number | null;
  purchaseNumber: string | null;
  expenseId: number | null;
  expenseNumber: string | null;
  amountApplied: number;
}

export interface Payment {
  id: number;
  paymentNumber: string;
  paymentDate: string;
  supplier: Supplier | null;
  amount: number;
  paymentMode: PaymentMode;
  remarks?: string;
  allocations: PaymentAllocation[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentAllocationPayload {
  purchaseId?: number;
  expenseId?: number;
  amountApplied: number;
}

export interface PaymentPayload {
  supplierId: number;
  paymentDate: string;
  amount: number;
  paymentMode: PaymentMode;
  remarks?: string;
  allocations?: PaymentAllocationPayload[];
}

export interface OutstandingPurchaseBill {
  purchaseId: number;
  purchaseNumber: string;
  purchaseDate: string;
  totalAmount: number;
  paidAmount: number;
  payableAmount: number;
}

export interface OutstandingExpense {
  expenseId: number;
  expenseNumber: string;
  expenseDate: string;
  category: string;
  totalAmount: number;
  paidAmount: number;
  payableAmount: number;
}

export interface SupplierOutstanding {
  supplierId: number;
  totalOutstanding: number;
  bills: OutstandingPurchaseBill[];
  expenses: OutstandingExpense[];
}

export interface PurchaseSummary {
  totalPurchaseOrders: number;
  pendingOrders: number;
  totalPurchaseBills: number;
  totalPayables: number;
  todaysPurchases: number;
}
