import api from './axios';
import { AlertItem } from '../types/alert';

export const alertApi = {
  list: () => api.get<AlertItem[]>('/alerts'),
};
