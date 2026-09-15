import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { BalanceSheetResponse } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table';
import { money, todayIso } from './reportFormat';

export default function BalanceSheet() {
  const navigate = useNavigate();
  const [asOfDate, setAsOfDate] = useState(todayIso());
  const [report, setReport] = useState<BalanceSheetResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (date: string) => {
    setLoading(true);
    try {
      const res = await accountingReportApi.balanceSheet(date);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Balance Sheet').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(asOfDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Balance Sheet"
        description={`Assets = Liabilities + Equity, as of a date. Equity is a report-level "Retained Earnings" line (accumulated Income − Expense) — this app has no dedicated Equity account type, so nothing is posted to make this figure; it is computed fresh on every load.${report ? ` Financial Year: ${report.currentFinancialYear}.` : ''}`}
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label htmlFor="asOfDate" className="text-xs text-muted-foreground">As of</Label>
              <Input id="asOfDate" type="date" className="w-40" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} />
            </div>
            <Button type="button" onClick={() => load(asOfDate)}>Apply</Button>
          </div>
        }
      />

      {loading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {[1, 2].map((i) => <Skeleton key={i} className="h-72 w-full" />)}
        </div>
      ) : (
        report && (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Assets</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <Table>
                    <TableBody>
                      {report.assetLines.map((l) => (
                        <TableRow key={l.accountId ?? l.accountName}>
                          <TableCell>{l.accountName}</TableCell>
                          <TableCell className="text-right">{money(l.amount)}</TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="bg-muted/40 font-semibold">
                        <TableCell>Total Assets</TableCell>
                        <TableCell className="text-right">{money(report.totalAssets)}</TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Liabilities & Equity</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <Table>
                    <TableBody>
                      {report.liabilityLines.map((l) => (
                        <TableRow key={l.accountId ?? l.accountName}>
                          <TableCell>{l.accountName}</TableCell>
                          <TableCell className="text-right">{money(l.amount)}</TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="font-medium">
                        <TableCell>Total Liabilities</TableCell>
                        <TableCell className="text-right">{money(report.totalLiabilities)}</TableCell>
                      </TableRow>
                      {report.equityLines.map((l) => (
                        <TableRow key={l.accountName}>
                          <TableCell>{l.accountName}</TableCell>
                          <TableCell className="text-right">{money(l.amount)}</TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="font-medium">
                        <TableCell>Total Equity</TableCell>
                        <TableCell className="text-right">{money(report.totalEquity)}</TableCell>
                      </TableRow>
                      <TableRow className="bg-muted/40 font-semibold">
                        <TableCell>Total Liabilities + Equity</TableCell>
                        <TableCell className="text-right">{money(report.totalLiabilities + report.totalEquity)}</TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardContent className="flex flex-wrap items-center justify-between gap-3 p-5">
                <div className="flex flex-wrap gap-6 text-sm">
                  <span>Current Year Profit ({report.currentFinancialYear}): <strong>{money(report.currentYearProfit)}</strong></span>
                  <span>Difference: <strong>{money(report.difference)}</strong></span>
                </div>
                <Badge variant={report.balanced ? 'success' : 'destructive'}>
                  {report.balanced ? 'Balanced' : 'Out of Balance'}
                </Badge>
              </CardContent>
            </Card>
          </>
        )
      )}
    </div>
  );
}
