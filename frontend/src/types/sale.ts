import { PaymentStatus, Product } from './purchase';

export type CustomerStatus = 'ACTIVE' | 'INACTIVE';

export interface Customer {
  id: number;
  firstName: string;
  lastName?: string;
  mobile: string;
  email?: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerCreatePayload {
  firstName: string;
  lastName?: string;
  mobile: string;
  email?: string;
}

export type SaleStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export interface SaleItem {
  id: number;
  product: Product;
  quantity: number;
  sellingPrice: number;
  discount: number;
  tax: number;
  subtotal: number;
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
  createdAt: string;
  updatedAt: string;
}

export interface SaleItemPayload {
  productId: number;
  quantity: number;
  sellingPrice: number;
  discount: number;
  tax: number;
}

export interface SaleCreatePayload {
  customerId: number | null;
  saleDate: string;
  paymentStatus: PaymentStatus;
  notes?: string;
  items: SaleItemPayload[];
}

export interface SaleUpdatePayload extends SaleCreatePayload {
  status: SaleStatus;
}
