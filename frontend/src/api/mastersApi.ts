import api from './axios';
import { PagedResponse } from '../types/user';
import {
  City,
  CityPayload,
  Country,
  CountryPayload,
  Currency,
  CurrencyPayload,
  Employee,
  EmployeePayload,
  Hsn,
  HsnPayload,
  ItemGroup,
  ItemGroupPayload,
  MasterStatus,
  Nationality,
  NationalityPayload,
  Party,
  PartyPayload,
  PartyType,
  StateMaster,
  StatePayload,
  Unit,
  UnitPayload,
  Zone,
  ZonePayload,
} from '../types/masters';

export interface MasterListQuery {
  search?: string;
  status?: MasterStatus | '';
  page?: number;
  size?: number;
}

const buildParams = (query: MasterListQuery, extra: Record<string, unknown> = {}) => ({
  search: query.search || undefined,
  status: query.status || undefined,
  page: query.page ?? 0,
  size: query.size ?? 10,
  ...extra,
});

export const currencyApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Currency>>('/masters/currencies', { params: buildParams(query) }),
  getById: (id: number) => api.get<Currency>(`/masters/currencies/${id}`),
  create: (payload: CurrencyPayload) => api.post<Currency>('/masters/currencies', payload),
  update: (id: number, payload: CurrencyPayload) => api.put<Currency>(`/masters/currencies/${id}`, payload),
  activate: (id: number) => api.patch<Currency>(`/masters/currencies/${id}/activate`),
  deactivate: (id: number) => api.patch<Currency>(`/masters/currencies/${id}/deactivate`),
};

export const countryApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Country>>('/masters/countries', { params: buildParams(query) }),
  getById: (id: number) => api.get<Country>(`/masters/countries/${id}`),
  create: (payload: CountryPayload) => api.post<Country>('/masters/countries', payload),
  update: (id: number, payload: CountryPayload) => api.put<Country>(`/masters/countries/${id}`, payload),
  activate: (id: number) => api.patch<Country>(`/masters/countries/${id}/activate`),
  deactivate: (id: number) => api.patch<Country>(`/masters/countries/${id}/deactivate`),
};

export interface StateListQuery extends MasterListQuery {
  countryId?: number | '';
}

export const stateApi = {
  list: (query: StateListQuery = {}) =>
    api.get<PagedResponse<StateMaster>>('/masters/states', {
      params: buildParams(query, { countryId: query.countryId || undefined }),
    }),
  getById: (id: number) => api.get<StateMaster>(`/masters/states/${id}`),
  create: (payload: StatePayload) => api.post<StateMaster>('/masters/states', payload),
  update: (id: number, payload: StatePayload) => api.put<StateMaster>(`/masters/states/${id}`, payload),
  activate: (id: number) => api.patch<StateMaster>(`/masters/states/${id}/activate`),
  deactivate: (id: number) => api.patch<StateMaster>(`/masters/states/${id}/deactivate`),
};

export interface CityListQuery extends MasterListQuery {
  stateId?: number | '';
  countryId?: number | '';
}

export const cityApi = {
  list: (query: CityListQuery = {}) =>
    api.get<PagedResponse<City>>('/masters/cities', {
      params: buildParams(query, { stateId: query.stateId || undefined, countryId: query.countryId || undefined }),
    }),
  getById: (id: number) => api.get<City>(`/masters/cities/${id}`),
  create: (payload: CityPayload) => api.post<City>('/masters/cities', payload),
  update: (id: number, payload: CityPayload) => api.put<City>(`/masters/cities/${id}`, payload),
  activate: (id: number) => api.patch<City>(`/masters/cities/${id}/activate`),
  deactivate: (id: number) => api.patch<City>(`/masters/cities/${id}/deactivate`),
};

export const zoneApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Zone>>('/masters/zones', { params: buildParams(query) }),
  getById: (id: number) => api.get<Zone>(`/masters/zones/${id}`),
  create: (payload: ZonePayload) => api.post<Zone>('/masters/zones', payload),
  update: (id: number, payload: ZonePayload) => api.put<Zone>(`/masters/zones/${id}`, payload),
  activate: (id: number) => api.patch<Zone>(`/masters/zones/${id}/activate`),
  deactivate: (id: number) => api.patch<Zone>(`/masters/zones/${id}/deactivate`),
};

export interface NationalityListQuery extends MasterListQuery {
  countryId?: number | '';
}

export const nationalityApi = {
  list: (query: NationalityListQuery = {}) =>
    api.get<PagedResponse<Nationality>>('/masters/nationalities', {
      params: buildParams(query, { countryId: query.countryId || undefined }),
    }),
  getById: (id: number) => api.get<Nationality>(`/masters/nationalities/${id}`),
  create: (payload: NationalityPayload) => api.post<Nationality>('/masters/nationalities', payload),
  update: (id: number, payload: NationalityPayload) => api.put<Nationality>(`/masters/nationalities/${id}`, payload),
  activate: (id: number) => api.patch<Nationality>(`/masters/nationalities/${id}/activate`),
  deactivate: (id: number) => api.patch<Nationality>(`/masters/nationalities/${id}/deactivate`),
};

export const unitApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Unit>>('/masters/units', { params: buildParams(query) }),
  getById: (id: number) => api.get<Unit>(`/masters/units/${id}`),
  create: (payload: UnitPayload) => api.post<Unit>('/masters/units', payload),
  update: (id: number, payload: UnitPayload) => api.put<Unit>(`/masters/units/${id}`, payload),
  activate: (id: number) => api.patch<Unit>(`/masters/units/${id}/activate`),
  deactivate: (id: number) => api.patch<Unit>(`/masters/units/${id}/deactivate`),
};

export const itemGroupApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<ItemGroup>>('/masters/item-groups', { params: buildParams(query) }),
  getById: (id: number) => api.get<ItemGroup>(`/masters/item-groups/${id}`),
  create: (payload: ItemGroupPayload) => api.post<ItemGroup>('/masters/item-groups', payload),
  update: (id: number, payload: ItemGroupPayload) => api.put<ItemGroup>(`/masters/item-groups/${id}`, payload),
  activate: (id: number) => api.patch<ItemGroup>(`/masters/item-groups/${id}/activate`),
  deactivate: (id: number) => api.patch<ItemGroup>(`/masters/item-groups/${id}/deactivate`),
};

export const hsnApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Hsn>>('/masters/hsn', { params: buildParams(query) }),
  getById: (id: number) => api.get<Hsn>(`/masters/hsn/${id}`),
  create: (payload: HsnPayload) => api.post<Hsn>('/masters/hsn', payload),
  update: (id: number, payload: HsnPayload) => api.put<Hsn>(`/masters/hsn/${id}`, payload),
  activate: (id: number) => api.patch<Hsn>(`/masters/hsn/${id}/activate`),
  deactivate: (id: number) => api.patch<Hsn>(`/masters/hsn/${id}/deactivate`),
};

export const employeeApi = {
  list: (query: MasterListQuery = {}) =>
    api.get<PagedResponse<Employee>>('/masters/employees', { params: buildParams(query) }),
  getById: (id: number) => api.get<Employee>(`/masters/employees/${id}`),
  create: (payload: EmployeePayload) => api.post<Employee>('/masters/employees', payload),
  update: (id: number, payload: EmployeePayload) => api.put<Employee>(`/masters/employees/${id}`, payload),
  activate: (id: number) => api.patch<Employee>(`/masters/employees/${id}/activate`),
  deactivate: (id: number) => api.patch<Employee>(`/masters/employees/${id}/deactivate`),
};

export interface PartyListQuery extends MasterListQuery {
  partyType?: PartyType | '';
}

export const partyApi = {
  list: (query: PartyListQuery = {}) =>
    api.get<PagedResponse<Party>>('/masters/parties', {
      params: buildParams(query, { partyType: query.partyType || undefined }),
    }),
  getById: (id: number) => api.get<Party>(`/masters/parties/${id}`),
  create: (payload: PartyPayload) => api.post<Party>('/masters/parties', payload),
  update: (id: number, payload: PartyPayload) => api.put<Party>(`/masters/parties/${id}`, payload),
  activate: (id: number) => api.patch<Party>(`/masters/parties/${id}/activate`),
  deactivate: (id: number) => api.patch<Party>(`/masters/parties/${id}/deactivate`),
};
