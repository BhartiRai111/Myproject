export type Role = 'ADMIN' | 'STORE_MANAGER' | 'STAFF';

export type UserStatus = 'ACTIVE' | 'INACTIVE';

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  password: string;
  confirmPassword: string;
  role: Role;
}

export interface UserCreatePayload {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  password: string;
  role: Role;
  status?: UserStatus;
}

export interface UserUpdatePayload {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  role: Role;
  status: UserStatus;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}
