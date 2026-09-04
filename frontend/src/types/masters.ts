export type MasterStatus = 'ACTIVE' | 'INACTIVE';

// ---------- Currency ----------
export interface Currency {
  id: number;
  name: string;
  code: string;
  symbol: string;
  decimalPlaces: number;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CurrencyPayload {
  name: string;
  code: string;
  symbol: string;
  decimalPlaces: number;
}

// ---------- Country ----------
export interface Country {
  id: number;
  name: string;
  code: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CountryPayload {
  name: string;
  code: string;
}

// ---------- State ----------
export interface StateMaster {
  id: number;
  name: string;
  code?: string;
  countryId: number;
  countryName: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StatePayload {
  name: string;
  code?: string;
  countryId: number;
}

// ---------- City ----------
export interface City {
  id: number;
  name: string;
  stateId: number;
  stateName: string;
  countryId: number;
  countryName: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CityPayload {
  name: string;
  stateId: number;
}

// ---------- Zone ----------
export interface Zone {
  id: number;
  name: string;
  code?: string;
  countryId?: number;
  countryName?: string;
  stateId?: number;
  stateName?: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ZonePayload {
  name: string;
  code?: string;
  countryId?: number | '';
  stateId?: number | '';
}

// ---------- Nationality ----------
export interface Nationality {
  id: number;
  name: string;
  countryId: number;
  countryName: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface NationalityPayload {
  name: string;
  countryId: number;
}

// ---------- Unit ----------
export interface Unit {
  id: number;
  name: string;
  symbol: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface UnitPayload {
  name: string;
  symbol: string;
}

// ---------- Item Group ----------
export interface ItemGroup {
  id: number;
  name: string;
  description?: string;
  status: MasterStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ItemGroupPayload {
  name: string;
  description?: string;
}

// ---------- HSN ----------
export interface HsnTaxRate {
  id?: number;
  taxPercent: number;
  cgstPercent?: number;
  sgstPercent?: number;
  igstPercent?: number;
  cessPercent?: number;
  effectiveFrom: string;
}

export interface Hsn {
  id: number;
  hsnCode: string;
  description?: string;
  status: MasterStatus;
  taxRates: HsnTaxRate[];
  createdAt: string;
  updatedAt: string;
}

export interface HsnPayload {
  hsnCode: string;
  description?: string;
  taxRates: HsnTaxRate[];
}

// ---------- Employee ----------
export interface Employee {
  id: number;
  employeeCode: string;
  name: string;
  mobile: string;
  email?: string;
  designation?: string;
  department?: string;
  address?: string;
  cityId?: number;
  cityName?: string;
  stateId?: number;
  stateName?: string;
  joiningDate?: string;
  status: MasterStatus;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeePayload {
  employeeCode: string;
  name: string;
  mobile: string;
  email?: string;
  designation?: string;
  department?: string;
  address?: string;
  cityId?: number | '';
  stateId?: number | '';
  joiningDate?: string;
  notes?: string;
}

// ---------- Party ----------
export type PartyType = 'SUPPLIER' | 'CUSTOMER' | 'BOTH';

export interface PartyAddress {
  id?: number;
  label: string;
  addressLine: string;
  cityId?: number;
  cityName?: string;
  stateId?: number;
  stateName?: string;
  pincode?: string;
}

export interface Party {
  id: number;
  partyCode: string;
  partyName: string;
  contactPerson?: string;
  mobile: string;
  email?: string;
  partyType: PartyType;
  gstNumber?: string;
  panNumber?: string;
  address?: string;
  cityId?: number;
  cityName?: string;
  stateId?: number;
  stateName?: string;
  countryId?: number;
  countryName?: string;
  pincode?: string;
  status: MasterStatus;
  notes?: string;
  dealsInCategoryIds: number[];
  dealsInCategoryNames: string[];
  addresses: PartyAddress[];
  createdAt: string;
  updatedAt: string;
}

export interface PartyPayload {
  partyCode: string;
  partyName: string;
  contactPerson?: string;
  mobile: string;
  email?: string;
  partyType: PartyType;
  gstNumber?: string;
  panNumber?: string;
  address?: string;
  cityId?: number | '';
  stateId?: number | '';
  countryId?: number | '';
  pincode?: string;
  notes?: string;
  dealsInCategoryIds: number[];
  addresses: PartyAddress[];
}
