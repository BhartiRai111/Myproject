import { Product } from './purchase';
import { Customer } from './sale';

export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_BILLED' | 'COMPLETED' | 'CANCELLED';

export interface SalesOrderItem {
  id: number;
  product: Product;
  quantity: number;
  rate: number;
  discount: number;
  gstPercent: number;
  taxableAmount: number;
  gstAmount: number;
  totalAmount: number;
  billedQuantity: number;
  remainingQuantity: number;
}

export interface SalesOrder {
  id: number;
  orderNumber: string;
  orderDate: string;
  customer: Customer | null;
  customerPhone?: string;
  customerGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  expectedDeliveryDate?: string;
  remarks?: string;
  status: SalesOrderStatus;
  totalAmount: number;
  billedAmount: number;
  remainingAmount: number;
  items: SalesOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface SalesOrderItemPayload {
  productId: number;
  quantity: number;
  rate: number;
  discount: number;
  gstPercent: number;
}

export interface SalesOrderPayload {
  customerId: number | null;
  customerPhone?: string;
  customerGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  orderDate: string;
  expectedDeliveryDate?: string;
  remarks?: string;
  items: SalesOrderItemPayload[];
}
