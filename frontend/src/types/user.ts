export type Role =
  | 'ADMIN'
  | 'STORE_MANAGER'
  | 'STAFF'
  | 'ACCOUNTANT'
  | 'SALES_USER'
  | 'PURCHASE_USER'
  | 'INVENTORY_USER';

export type UserStatus = 'ACTIVE' | 'INACTIVE';

export type Permission = string;

export interface RoleInfo {
  name: Role;
  description: string;
  active: boolean;
  permissionCount: number;
}

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  role: Role;
  status: UserStatus;
  employeeId?: number | null;
  employeeName?: string | null;
  employeeCode?: string | null;
  mustChangePassword: boolean;
  lastLogin?: string | null;
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
  employeeId?: number | null;
}

export interface UserUpdatePayload {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  role: Role;
  status: UserStatus;
  employeeId?: number | null;
}

export interface AdminPasswordResetPayload {
  newPassword: string;
  confirmPassword: string;
}

export interface PasswordChangePayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
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
