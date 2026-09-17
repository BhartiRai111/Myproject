import api from './axios';
import { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/user';
import { Store } from '../types/store';

export const authApi = {
  login: (payload: LoginPayload) => api.post<AuthResponse>('/auth/login', payload),
  register: (payload: RegisterPayload) => api.post<User>('/auth/register', payload),
  logout: () => api.post('/auth/logout'),
  me: () => api.get<User>('/auth/me'),
  /** Every store the caller may act on — all stores for an ALL_STORES user, else their explicit assignments. */
  getMyStores: () => api.get<Store[]>('/auth/my-stores'),
  setCurrentStore: (storeId: number) => api.put<User>('/auth/current-store', { storeId }),
};
