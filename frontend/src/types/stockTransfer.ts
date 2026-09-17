export type StockTransferStatus = 'DRAFT' | 'APPROVED' | 'DISPATCHED' | 'RECEIVED' | 'CANCELLED';

export interface StockTransferItem {
  id: number;
  productId: number;
  productName: string;
  sku?: string | null;
  quantity: number;
  notes?: string | null;
}

export interface StockTransfer {
  id: number;
  transferNumber: string;
  transferDate: string;
  fromStoreId: number;
  fromStoreName: string;
  fromStoreCode: string;
  toStoreId: number;
  toStoreName: string;
  toStoreCode: string;
  status: StockTransferStatus;
  remarks?: string | null;
  items: StockTransferItem[];
  createdBy?: string | null;
  createdAt: string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  dispatchedBy?: string | null;
  dispatchedAt?: string | null;
  receivedBy?: string | null;
  receivedAt?: string | null;
  cancelledBy?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
}

export interface StockTransferItemPayload {
  productId: number;
  quantity: number;
  notes?: string;
}

export interface StockTransferPayload {
  transferDate: string;
  fromStoreId: number;
  toStoreId: number;
  remarks?: string;
  items: StockTransferItemPayload[];
}
