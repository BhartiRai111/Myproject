export interface ImportResult {
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: string[];
}
