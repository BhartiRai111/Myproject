import { Customer, GstType } from './sale';
import { Supplier } from './supplier';

export type NoteStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';
export type StockImpactType = 'STOCK_RETURN' | 'FINANCIAL_ADJUSTMENT';
export type CreditNoteType = 'SALES_RETURN' | 'PRICE_ADJUSTMENT' | 'DISCOUNT_ADJUSTMENT' | 'TAX_ADJUSTMENT' | 'CUSTOMER_CREDIT';
export type DebitNoteType = 'PURCHASE_RETURN' | 'SUPPLIER_DEBIT' | 'PRICE_ADJUSTMENT' | 'TAX_ADJUSTMENT';

export interface CreditNoteItem {
  id: number;
  productId: number;
  productName: string;
  saleItemId: number;
  quantity: number;
  rate: number;
  discount: number;
  taxableAmount: number;
  gstPercent?: number;
  cgstAmount?: number;
  sgstAmount?: number;
  igstAmount?: number;
  total: number;
}

export interface CreditNote {
  id: number;
  voucherNumber: string;
  noteType: CreditNoteType;
  sourceSaleId: number;
  sourceSaleInvoiceNumber: string;
  customer: Customer;
  noteDate: string;
  reason?: string;
  placeOfSupply?: string;
  gstType?: GstType;
  stockImpact: StockImpactType;
  gstReportingApplicable: boolean;
  items: CreditNoteItem[];
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  totalAmount: number;
  status: NoteStatus;
  remarks?: string;
  createdBy?: string;
  postedBy?: string;
  postedAt?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreditNoteItemPayload {
  saleItemId: number;
  quantity: number;
}

export interface CreditNoteCreatePayload {
  sourceSaleId: number;
  noteType: CreditNoteType;
  noteDate: string;
  stockImpact: StockImpactType;
  reason?: string;
  remarks?: string;
  items: CreditNoteItemPayload[];
  post?: boolean;
}

export interface DebitNoteItem {
  id: number;
  productId: number;
  productName: string;
  purchaseItemId: number;
  quantity: number;
  rate: number;
  discount: number;
  taxableAmount: number;
  gstPercent?: number;
  cgstAmount?: number;
  sgstAmount?: number;
  igstAmount?: number;
  total: number;
}

export interface DebitNote {
  id: number;
  voucherNumber: string;
  noteType: DebitNoteType;
  sourcePurchaseId: number;
  sourcePurchaseNumber: string;
  supplier: Supplier;
  noteDate: string;
  reason?: string;
  placeOfSupply?: string;
  gstType?: GstType;
  stockImpact: StockImpactType;
  gstReportingApplicable: boolean;
  items: DebitNoteItem[];
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  totalAmount: number;
  status: NoteStatus;
  remarks?: string;
  createdBy?: string;
  postedBy?: string;
  postedAt?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DebitNoteItemPayload {
  purchaseItemId: number;
  quantity: number;
}

export interface DebitNoteCreatePayload {
  sourcePurchaseId: number;
  noteType: DebitNoteType;
  noteDate: string;
  stockImpact: StockImpactType;
  reason?: string;
  remarks?: string;
  items: DebitNoteItemPayload[];
  post?: boolean;
}
