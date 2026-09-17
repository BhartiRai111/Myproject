import { MasterStatus } from './masters';

export type StoreType = 'STORE' | 'WAREHOUSE' | 'HEAD_OFFICE';

export interface Store {
  id: number;
  storeCode: string;
  storeName: string;
  storeType: StoreType;
  legalName?: string | null;
  address?: string | null;
  countryId?: number | null;
  countryName?: string | null;
  stateId?: number | null;
  stateName?: string | null;
  stateCode?: string | null;
  cityId?: number | null;
  cityName?: string | null;
  zoneId?: number | null;
  zoneName?: string | null;
  pincode?: string | null;
  phone?: string | null;
  email?: string | null;
  gstin?: string | null;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StorePayload {
  storeCode: string;
  storeName: string;
  storeType: StoreType;
  legalName: string;
  address: string;
  countryId: number | '';
  stateId: number | '';
  cityId: number | '';
  zoneId: number | '';
  pincode: string;
  phone: string;
  email: string;
  gstin: string;
}

export interface StoreGstContext {
  storeId: number;
  gstin?: string | null;
  legalName?: string | null;
  stateCode?: string | null;
  /** "STORE" when the store's own GSTIN override was used, "BUSINESS" when it fell back to the business-wide config. */
  source: 'STORE' | 'BUSINESS';
}
