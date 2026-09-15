import api from './axios';
import { DayClosingCloseInput, DayClosingSummary } from '../types/dayClosing';

export const dayClosingApi = {
  summary: (date: string) => api.get<DayClosingSummary>('/day-closing/summary', { params: { date } }),
  history: () => api.get<DayClosingSummary[]>('/day-closing/history'),
  close: (payload: DayClosingCloseInput) => api.post<DayClosingSummary>('/day-closing/close', payload),
};
