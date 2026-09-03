import api from './axios';
import { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/user';

export const authApi = {
  login: (payload: LoginPayload) => api.post<AuthResponse>('/auth/login', payload),
  register: (payload: RegisterPayload) => api.post<User>('/auth/register', payload),
  logout: () => api.post('/auth/logout'),
  me: () => api.get<User>('/auth/me'),
};
