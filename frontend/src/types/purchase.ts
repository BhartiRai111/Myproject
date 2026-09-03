import type { Product } from './product';

export type { Product };

export interface Supplier {
  id: number;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierCreatePayload {
  name: string;
  phone?: string;
  email?: string;
  address?: string;
}

export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID';

export type PurchaseStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export interface PurchaseItem {
  id: number;
  product: Product;
  quantity: number;
  purchasePrice: number;
  discount: number;
  tax: number;
  subtotal: number;
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
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseItemPayload {
  productId: number;
  quantity: number;
  purchasePrice: number;
  discount: number;
  tax: number;
}

export interface PurchaseCreatePayload {
  supplierId: number;
  purchaseDate: string;
  paymentStatus: PaymentStatus;
  notes?: string;
  items: PurchaseItemPayload[];
}

export interface PurchaseUpdatePayload extends PurchaseCreatePayload {
  status: PurchaseStatus;
}
