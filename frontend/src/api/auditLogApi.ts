import api from './axios';
import { PagedResponse } from '../types/user';
import { AuditAction, AuditLog } from '../types/auditLog';

export interface AuditLogQuery {
  action?: AuditAction | '';
  module?: string;
  entityType?: string;
  entityId?: number;
  userId?: number;
  search?: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const auditLogApi = {
  list: (query: AuditLogQuery) =>
    api.get<PagedResponse<AuditLog>>('/audit-logs', {
      params: {
        action: query.action || undefined,
        module: query.module || undefined,
        entityType: query.entityType || undefined,
        entityId: query.entityId || undefined,
        userId: query.userId || undefined,
        search: query.search || undefined,
        fromDate: query.fromDate || undefined,
        toDate: query.toDate || undefined,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    }),
};
