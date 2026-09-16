import { PaymentStatus, Product } from './purchase';

export type CustomerStatus = 'ACTIVE' | 'INACTIVE';

export interface Customer {
  id: number;
  firstName: string;
  lastName?: string;
  mobile: string;
  email?: string;
  state?: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerCreatePayload {
  firstName: string;
  lastName?: string;
  mobile: string;
  email?: string;
  state?: string;
}

export type SaleStatus = 'DRAFT' | 'PENDING' | 'COMPLETED' | 'CANCELLED';

export type SaleTransactionType = 'SALE' | 'SALE_CHALLAN';

export type GstType = 'GST' | 'NON_GST';
export type TaxMode = 'INTRA_STATE' | 'INTER_STATE';
export type PaymentMode = 'CASH' | 'BANK' | 'UPI' | 'CARD' | 'OTHER';

export interface SaleItem {
  id: number;
  product: Product;
  quantity: number;
  sellingPrice: number;
  discount: number;
  tax: number;
  subtotal: number;
  gstPercent?: number;
  taxableAmount?: number;
  cgstAmount?: number;
  sgstAmount?: number;
  igstAmount?: number;
  salesOrderItemId?: number | null;
}

export interface Sale {
  id: number;
  invoiceNumber: string;
  customer: Customer | null;
  saleDate: string;
  items: SaleItem[];
  subtotalAmount: number;
  totalDiscount: number;
  totalTax: number;
  totalAmount: number;
  paymentStatus: PaymentStatus;
  status: SaleStatus;
  notes?: string;
  gstType: GstType;
  taxMode?: TaxMode | null;
  customerPhone?: string;
  customerGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  paymentMode: PaymentMode;
  paidAmount: number;
  dueAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  salesOrderId?: number | null;
  salesOrderNumber?: string | null;
  hasReceipts: boolean;
  transactionType: SaleTransactionType;
  gstReportingApplicable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaleItemPayload {
  productId: number;
  quantity: number;
  sellingPrice: number;
  discount: number;
  tax: number;
  gstPercent?: number;
  salesOrderItemId?: number;
}

export interface SaleCreatePayload {
  /** Idempotency key (Phase 6): the SAME value resent on a retry/double-click returns the original Sale instead of creating a duplicate. */
  clientRequestId?: string;
  customerId: number | null;
  saleDate: string;
  gstType: GstType;
  taxMode?: TaxMode | '';
  customerPhone?: string;
  customerGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  paymentMode: PaymentMode;
  paidAmount: number;
  notes?: string;
  salesOrderId?: number;
  items: SaleItemPayload[];
  transactionType?: SaleTransactionType;
  saveAsDraft?: boolean;
}

export interface SaleUpdatePayload extends SaleCreatePayload {
  status: SaleStatus;
}
