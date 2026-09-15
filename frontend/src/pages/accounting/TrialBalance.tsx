import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { accountingReportApi } from '../../api/accountingApi';
import { parseApiError } from '../../utils/apiError';
import { TrialBalanceResponse } from '../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const money = (n: number) => (n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const todayIso = () => new Date().toISOString().slice(0, 10);

export default function TrialBalance() {
  const navigate = useNavigate();
  const [asOfDate, setAsOfDate] = useState(todayIso());
  const [report, setReport] = useState<TrialBalanceResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (date: string) => {
    setLoading(true);
    try {
      const res = await accountingReportApi.trialBalance(date);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load trial balance').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(asOfDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDateChange = (value: string) => {
    setAsOfDate(value);
    load(value);
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Trial Balance"
        description="Account-wise debit and credit balances, derived live from posted journals."
        actions={
          <div className="flex items-center gap-2">
            <Label htmlFor="asOfDate" className="text-sm text-muted-foreground">
              As of
            </Label>
            <Input id="asOfDate" type="date" className="w-40" value={asOfDate} onChange={(e) => handleDateChange(e.target.value)} />
          </div>
        }
      />

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Account</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="text-right">Debit</TableHead>
                <TableHead className="text-right">Credit</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={5} />
            ) : (
              <TableBody>
                {report?.rows.map((r) => (
                  <TableRow key={r.accountId}>
                    <TableCell className="font-mono text-sm">{r.accountCode}</TableCell>
                    <TableCell className="font-medium">{r.accountName}</TableCell>
                    <TableCell className="text-muted-foreground">{r.accountType}</TableCell>
                    <TableCell className="text-right">{r.debit > 0 ? `₹${money(r.debit)}` : '—'}</TableCell>
                    <TableCell className="text-right">{r.credit > 0 ? `₹${money(r.credit)}` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && report && (
            <div className="flex flex-wrap items-center justify-end gap-6 border-t border-border p-4 text-sm font-semibold">
              <span>Total Debit: ₹{money(report.totalDebit)}</span>
              <span>Total Credit: ₹{money(report.totalCredit)}</span>
              <span>Difference: ₹{money(report.difference)}</span>
              <Badge variant={report.balanced ? 'success' : 'destructive'}>
                {report.balanced ? 'Balanced' : 'Out of Balance'}
              </Badge>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
