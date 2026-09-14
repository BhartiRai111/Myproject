import type { Product } from './product';
import type { Supplier } from './supplier';

export type { Product };
export type { Supplier };

export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID';

export type PurchaseStatus = 'DRAFT' | 'PENDING' | 'COMPLETED' | 'CANCELLED';

export type PurchaseTransactionType = 'PURCHASE' | 'PURCHASE_CHALLAN';

export type GstType = 'GST' | 'NON_GST';
export type TaxMode = 'INTRA_STATE' | 'INTER_STATE';
export type PaymentMode = 'CASH' | 'BANK' | 'UPI' | 'CARD' | 'OTHER';

export interface PurchaseItem {
  id: number;
  product: Product;
  quantity: number;
  purchasePrice: number;
  discount: number;
  tax: number;
  subtotal: number;
  gstPercent?: number;
  taxableAmount?: number;
  cgstAmount?: number;
  sgstAmount?: number;
  igstAmount?: number;
  purchaseOrderItemId?: number | null;
}

export interface Purchase {
  id: number;
  purchaseNumber: string;
  supplier: Supplier;
  purchaseDate: string;
  items: PurchaseItem[];
  subtotalAmount: number;
  totalDiscount: number;
  totalTax: number;
  totalAmount: number;
  paymentStatus: PaymentStatus;
  status: PurchaseStatus;
  notes?: string;
  gstType: GstType;
  taxMode?: TaxMode | null;
  supplierPhone?: string;
  supplierGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  paymentMode: PaymentMode;
  paidAmount: number;
  payableAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  purchaseOrderId?: number | null;
  purchaseOrderNumber?: string | null;
  hasPayments: boolean;
  transactionType: PurchaseTransactionType;
  gstReportingApplicable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseItemPayload {
  productId: number;
  quantity: number;
  purchasePrice: number;
  discount: number;
  tax: number;
  gstPercent?: number;
  purchaseOrderItemId?: number;
}

export interface PurchaseCreatePayload {
  supplierId: number;
  purchaseDate: string;
  gstType: GstType;
  taxMode?: TaxMode | '';
  supplierPhone?: string;
  supplierGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  paymentMode: PaymentMode;
  paidAmount: number;
  notes?: string;
  purchaseOrderId?: number;
  items: PurchaseItemPayload[];
  transactionType?: PurchaseTransactionType;
  saveAsDraft?: boolean;
}

export interface PurchaseUpdatePayload extends PurchaseCreatePayload {
  status: PurchaseStatus;
}
