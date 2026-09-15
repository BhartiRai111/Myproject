import api from './axios';
import { PagedResponse } from '../types/user';
import { DebitNote, DebitNoteCreatePayload, NoteStatus } from '../types/note';

export interface DebitNoteQuery {
  search?: string;
  status?: NoteStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const debitNoteApi = {
  list: (query: DebitNoteQuery) =>
    api.get<PagedResponse<DebitNote>>('/debit-notes', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<DebitNote>(`/debit-notes/${id}`),
  create: (payload: DebitNoteCreatePayload) => api.post<DebitNote>('/debit-notes', payload),
  post: (id: number) => api.post<DebitNote>(`/debit-notes/${id}/post`),
  cancel: (id: number) => api.patch<DebitNote>(`/debit-notes/${id}/cancel`),
};
