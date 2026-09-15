import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Landmark } from 'lucide-react';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { CashBankBookResponse } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

export default function BankBook() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<CashBankBookResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.bankBook(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Bank Book').message);
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
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Bank Book"
        description={report ? `${report.accountName} (${report.accountCode}) — every bank receipt and payment from the accounting journal.` : 'Every bank receipt and payment, read from the Bank account in the accounting journal.'}
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

      {!loading && report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Opening Bank Balance</p>
              <p className="text-lg font-bold">{money(report.openingBalance)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Receipts</p>
              <p className="text-lg font-bold text-success">{money(report.totalReceipts)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Payments</p>
              <p className="text-lg font-bold text-destructive">{money(report.totalPayments)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Closing Bank Balance</p>
              <p className="text-lg font-bold">{money(report.closingBalance)}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Date</TableHead>
                <TableHead>Voucher</TableHead>
                <TableHead>Particulars</TableHead>
                <TableHead className="text-right">Receipt</TableHead>
                <TableHead className="text-right">Payment</TableHead>
                <TableHead className="text-right">Running Balance</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={6} />
            ) : (
              <TableBody>
                {report?.rows.map((r, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{r.voucherDate}</TableCell>
                    <TableCell>{r.voucherType}{r.voucherNumber ? ` · ${r.voucherNumber}` : ''}</TableCell>
                    <TableCell className="text-muted-foreground">{r.particulars || '—'}</TableCell>
                    <TableCell className="text-right text-success">{r.receipt ? money(r.receipt) : '—'}</TableCell>
                    <TableCell className="text-right text-destructive">{r.payment ? money(r.payment) : '—'}</TableCell>
                    <TableCell className="text-right font-medium">{money(r.runningBalance)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && (report?.rows.length ?? 0) === 0 && (
            <EmptyState icon={Landmark} title="No bank activity" description="No bank receipts or payments in this range." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
