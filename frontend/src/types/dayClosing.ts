export interface DayClosingSummary {
  closingDate: string;
  closed: boolean;
  totalSales: number;
  totalPurchases: number;
  cashSales: number;
  cardSales: number;
  upiSales: number;
  creditSales: number;
  totalReceipts: number;
  totalPayments: number;
  totalExpenses: number;
  cashIn: number;
  cashOut: number;
  expectedCash: number;
  actualCash?: number;
  difference?: number;
  differenceReason?: string;
  closedBy?: string;
  closedAt?: string;
}

export interface DayClosingCloseInput {
  closingDate: string;
  actualCash: number;
  differenceReason?: string;
}
