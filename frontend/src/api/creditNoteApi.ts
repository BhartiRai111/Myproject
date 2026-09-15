import api from './axios';
import { PagedResponse } from '../types/user';
import { CreditNote, CreditNoteCreatePayload, NoteStatus } from '../types/note';

export interface CreditNoteQuery {
  search?: string;
  status?: NoteStatus | '';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const creditNoteApi = {
  list: (query: CreditNoteQuery) =>
    api.get<PagedResponse<CreditNote>>('/credit-notes', {
      params: {
        search: query.search || undefined,
        status: query.status || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<CreditNote>(`/credit-notes/${id}`),
  create: (payload: CreditNoteCreatePayload) => api.post<CreditNote>('/credit-notes', payload),
  post: (id: number) => api.post<CreditNote>(`/credit-notes/${id}/post`),
  cancel: (id: number) => api.patch<CreditNote>(`/credit-notes/${id}/cancel`),
};
