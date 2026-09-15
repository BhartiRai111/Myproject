export type AlertSeverity = 'CRITICAL' | 'WARNING';

export interface AlertItem {
  severity: AlertSeverity;
  category: string;
  message: string;
  path?: string;
}
