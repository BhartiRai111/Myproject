import api from './axios';
import { StoreComparisonResponse } from '../types/storeComparison';

export const storeComparisonApi = {
  compare: (fromDate: string, toDate: string) =>
    api.get<StoreComparisonResponse>('/reports/store-comparison', { params: { fromDate, toDate } }),
};
