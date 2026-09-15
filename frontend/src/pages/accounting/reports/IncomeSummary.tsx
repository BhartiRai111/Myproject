import { accountingReportApi } from '../../../api/accountingApi';
import ExpenseIncomeSummaryView from './ExpenseIncomeSummaryView';

export default function IncomeSummary() {
  return (
    <ExpenseIncomeSummaryView
      title="Income Summary"
      description="Every income account, grouped, with its posted vouchers for the date range."
      totalLabel="Total Income"
      fetcher={accountingReportApi.incomeSummary}
    />
  );
}
