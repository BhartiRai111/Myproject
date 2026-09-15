export type AuditAction =
  | 'CREATE'
  | 'UPDATE'
  | 'POST'
  | 'CANCEL'
  | 'DELETE'
  | 'LOGIN'
  | 'LOGOUT'
  | 'PRINT'
  | 'EXPORT'
  | 'PAYMENT'
  | 'RECEIPT'
  | 'APPROVE'
  | 'REVERSE'
  | 'FY_CLOSE'
  | 'FY_OPEN'
  | 'PERMISSION_CHANGE';

export interface AuditLog {
  id: number;
  userId?: number;
  username?: string;
  action: AuditAction;
  module?: string;
  entityType?: string;
  entityId?: number;
  documentNumber?: string;
  oldValue?: string;
  newValue?: string;
  timestamp: string;
  ipAddress?: string;
  userAgent?: string;
  description?: string;
}
