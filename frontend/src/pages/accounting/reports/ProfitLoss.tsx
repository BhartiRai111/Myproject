import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { ProfitLossResponse } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

export default function ProfitLoss() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<ProfitLossResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.profitLoss(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Profit & Loss').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isProfit = (report?.netProfitOrLoss ?? 0) >= 0;

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Profit & Loss"
        description="Derived from accounts classified Income/Expense in the Chart of Accounts — never calculated directly from Sale/Purchase tables."
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
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {[1, 2].map((i) => <Skeleton key={i} className="h-64 w-full" />)}
        </div>
      ) : (
        report && (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Income</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <Table>
                    <TableBody>
                      {report.incomeLines.map((l) => (
                        <TableRow key={l.accountId}>
                          <TableCell>{l.accountName}</TableCell>
                          <TableCell className="text-right">{money(l.amount)}</TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="bg-muted/40 font-semibold">
                        <TableCell>Total Income</TableCell>
                        <TableCell className="text-right">{money(report.totalIncome)}</TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Expenses</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <Table>
                    <TableBody>
                      {report.expenseLines.map((l) => (
                        <TableRow key={l.accountId}>
                          <TableCell>{l.accountName}</TableCell>
                          <TableCell className="text-right">{money(l.amount)}</TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="bg-muted/40 font-semibold">
                        <TableCell>Total Expenses</TableCell>
                        <TableCell className="text-right">{money(report.totalExpense)}</TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardContent className="flex items-center justify-between p-5">
                <span className="text-base font-semibold">{isProfit ? 'Net Profit' : 'Net Loss'}</span>
                <span className={`text-xl font-bold ${isProfit ? 'text-success' : 'text-destructive'}`}>
                  {money(Math.abs(report.netProfitOrLoss))}
                </span>
              </CardContent>
            </Card>
          </>
        )
      )}
    </div>
  );
}
