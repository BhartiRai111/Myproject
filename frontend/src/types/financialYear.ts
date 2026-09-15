export type FinancialYearStatus = 'OPEN' | 'CLOSED';

export interface FinancialYear {
  id: number;
  name: string;
  code: string;
  startDate: string;
  endDate: string;
  status: FinancialYearStatus;
  current: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FinancialYearCreatePayload {
  name?: string;
  code?: string;
  startDate: string;
  endDate: string;
}

export interface FinancialYearSummary {
  financialYear: FinancialYear;
  totalSales: number;
  totalPurchases: number;
  saleCount: number;
  purchaseCount: number;
}
