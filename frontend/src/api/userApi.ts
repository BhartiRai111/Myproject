import api from './axios';
import {
  PagedResponse,
  Role,
  User,
  UserCreatePayload,
  UserStatus,
  UserUpdatePayload,
} from '../types/user';

export interface UserQuery {
  search?: string;
  role?: Role | '';
  status?: UserStatus | '';
  page?: number;
  size?: number;
}

export const userApi = {
  list: (query: UserQuery) =>
    api.get<PagedResponse<User>>('/users', {
      params: {
        search: query.search || undefined,
        role: query.role || undefined,
        status: query.status || undefined,
        page: query.page ?? 0,
        size: query.size ?? 10,
      },
    }),
  getById: (id: number) => api.get<User>(`/users/${id}`),
  create: (payload: UserCreatePayload) => api.post<User>('/users', payload),
  update: (id: number, payload: UserUpdatePayload) => api.put<User>(`/users/${id}`, payload),
  updateStatus: (id: number, status: UserStatus) =>
    api.patch<User>(`/users/${id}/status`, { status }),
};
