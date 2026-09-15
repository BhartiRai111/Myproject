import { PaymentMode } from './sale';

export interface PaymentMethod {
  id: number;
  name: string;
  type: PaymentMode;
  active: boolean;
  sortOrder?: number;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentMethodPayload {
  name: string;
  type: PaymentMode;
  sortOrder?: number;
}
