import api from './axios';
import { GlobalSearchResponse } from '../types/globalSearch';

export const globalSearchApi = {
  search: (q: string) => api.get<GlobalSearchResponse>('/search', { params: { q } }),
};
