import api from './axios';
import { Role, RoleInfo } from '../types/user';

export interface PermissionEntry {
  name: string;
  module: string;
}

export type PermissionsByModule = Record<string, PermissionEntry[]>;

export const roleApi = {
  list: () => api.get<RoleInfo[]>('/roles'),
  getPermissions: (role: Role) => api.get<PermissionsByModule>(`/roles/${role}/permissions`),
};

export const permissionApi = {
  list: () => api.get<PermissionsByModule>('/permissions'),
};
