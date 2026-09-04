export type SupplierStatus = 'ACTIVE' | 'INACTIVE';

export interface Supplier {
  id: number;
  name: string;
  contactPerson?: string;
  mobile: string;
  email?: string;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
  gstNumber?: string;
  notes?: string;
  status: SupplierStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierPayload {
  name: string;
  contactPerson?: string;
  mobile: string;
  email?: string;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
  gstNumber?: string;
  notes?: string;
}

export type SupplierPurchaseStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';
export type SupplierPurchasePaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID';

export interface SupplierPurchaseSummary {
  id: number;
  purchaseNumber: string;
  purchaseDate: string;
  totalAmount: number;
  paymentStatus: SupplierPurchasePaymentStatus;
  status: SupplierPurchaseStatus;
}
