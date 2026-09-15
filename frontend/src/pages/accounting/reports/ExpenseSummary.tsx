import { accountingReportApi } from '../../../api/accountingApi';
import ExpenseIncomeSummaryView from './ExpenseIncomeSummaryView';

export default function ExpenseSummary() {
  return (
    <ExpenseIncomeSummaryView
      title="Expense Summary"
      description="Every expense account, grouped, with its posted vouchers for the date range."
      totalLabel="Total Expenses"
      fetcher={accountingReportApi.expenseSummary}
    />
  );
}
