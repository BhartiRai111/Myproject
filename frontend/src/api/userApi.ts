import api from './axios';
import {
  AdminPasswordResetPayload,
  PagedResponse,
  PasswordChangePayload,
  Permission,
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
  resetPassword: (id: number, payload: AdminPasswordResetPayload) =>
    api.post<User>(`/users/${id}/reset-password`, payload),
  changeOwnPassword: (payload: PasswordChangePayload) => api.put<void>('/users/me/password', payload),
  getEffectivePermissions: (id: number) => api.get<Permission[]>(`/users/${id}/effective-permissions`),
  /** This user's ASSIGNED_STORES list (Multi-Store spec sections 11, 70) — irrelevant for a STORE_ACCESS_ALL user, who can already act on every store. */
  getAssignedStores: (id: number) => api.get<number[]>(`/users/${id}/stores`),
  assignStores: (id: number, storeIds: number[]) => api.put<User>(`/users/${id}/stores`, { storeIds }),
};
