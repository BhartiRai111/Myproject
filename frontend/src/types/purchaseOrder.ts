import { Product } from './product';
import { Supplier } from './supplier';

export type PurchaseOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CANCELLED';

export interface PurchaseOrderItem {
  id: number;
  product: Product;
  quantity: number;
  rate: number;
  discount: number;
  gstPercent: number;
  taxableAmount: number;
  gstAmount: number;
  totalAmount: number;
  receivedQuantity: number;
  remainingQuantity: number;
}

export interface PurchaseOrder {
  id: number;
  orderNumber: string;
  orderDate: string;
  supplier: Supplier;
  supplierPhone?: string;
  supplierGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  expectedDeliveryDate?: string;
  remarks?: string;
  status: PurchaseOrderStatus;
  totalAmount: number;
  receivedAmount: number;
  remainingAmount: number;
  items: PurchaseOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseOrderItemPayload {
  productId: number;
  quantity: number;
  rate: number;
  discount: number;
  gstPercent: number;
}

export interface PurchaseOrderPayload {
  supplierId: number;
  supplierPhone?: string;
  supplierGstin?: string;
  billingAddress?: string;
  shippingAddress?: string;
  orderDate: string;
  expectedDeliveryDate?: string;
  remarks?: string;
  items: PurchaseOrderItemPayload[];
}
