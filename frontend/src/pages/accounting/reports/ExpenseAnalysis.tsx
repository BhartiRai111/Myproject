import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { BarChart3 } from 'lucide-react';
import { expenseApi } from '../../../api/expenseApi';
import { parseApiError } from '../../../utils/apiError';
import { ExpenseSummaryReportResponse } from '../../../types/expense';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

function GroupTable({ title, rows }: { title: string; rows: { key: string; label: string; totalAmount: number; count: number }[] }) {
  return (
    <Card>
      <CardContent className="p-0">
        <div className="border-b border-border p-4 font-semibold">{title}</div>
        {rows.length === 0 ? (
          <div className="p-4 text-sm text-muted-foreground">No activity in this range.</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{title.replace(' Summary', '')}</TableHead>
                <TableHead className="text-right">Count</TableHead>
                <TableHead className="text-right">Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.key}>
                  <TableCell>{r.label}</TableCell>
                  <TableCell className="text-right">{r.count}</TableCell>
                  <TableCell className="text-right font-medium">{money(r.totalAmount)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

export default function ExpenseAnalysis() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<ExpenseSummaryReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await expenseApi.summary(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Expense Analysis').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-6">
      <BackButton label="Back to Expenses" onClick={() => navigate('/accounting/expenses')} />

      <PageHeader
        title="Expense Analysis"
        description="Category-wise, payment-method-wise, party-wise, and GST/ITC-wise expense totals — aggregated server-side for the date range."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label htmlFor="fromDate" className="text-xs text-muted-foreground">From</Label>
              <Input id="fromDate" type="date" className="w-40" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </div>
            <div>
              <Label htmlFor="toDate" className="text-xs text-muted-foreground">To</Label>
              <Input id="toDate" type="date" className="w-40" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </div>
            <Button type="button" onClick={load}>Apply</Button>
          </div>
        }
      />

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <Skeleton key={i} className="h-32 w-full" />)}
        </div>
      ) : !report || report.totalCount === 0 ? (
        <EmptyState icon={BarChart3} title="No activity" description="No posted expenses in this range." />
      ) : (
        <>
          <Card>
            <CardContent className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-5">
              <div>
                <p className="text-xs text-muted-foreground">Total Expenses</p>
                <p className="text-lg font-bold">{money(report.totalAmount)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Count</p>
                <p className="text-lg font-bold">{report.totalCount}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">GST Applicable</p>
                <p className="text-lg font-bold">{money(report.gstApplicableAmount)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">ITC Eligible Tax</p>
                <p className="text-lg font-bold">{money(report.itcEligibleTax)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">ITC Ineligible Tax</p>
                <p className="text-lg font-bold">{money(report.itcIneligibleTax)}</p>
              </div>
            </CardContent>
          </Card>

          <GroupTable title="Category-wise Summary" rows={report.byCategory} />
          <GroupTable title="Payment-Method-wise Summary" rows={report.byPaymentMode} />
          <GroupTable title="Party-wise Summary" rows={report.byParty} />
        </>
      )}
    </div>
  );
}
