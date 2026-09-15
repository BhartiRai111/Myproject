import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { BookText } from 'lucide-react';
import { accountingReportApi, journalApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { DayBookResponse, JournalHeader, VoucherType } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

const ALL = '__all__';
const VOUCHER_TYPES: VoucherType[] = ['SALE', 'PURCHASE', 'RECEIPT', 'PAYMENT', 'JOURNAL', 'CREDIT_NOTE', 'DEBIT_NOTE'];

const statusVariant = (status: string) => {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'REVERSED') return 'muted' as const;
  return 'warning' as const;
};

export default function DayBook() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [voucherType, setVoucherType] = useState<VoucherType | ''>('');
  const [report, setReport] = useState<DayBookResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [viewJournal, setViewJournal] = useState<JournalHeader | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.dayBook(fromDate, toDate, voucherType || undefined);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Day Book').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openJournal = async (journalId: number) => {
    try {
      const res = await journalApi.getById(journalId);
      setViewJournal(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load journal details').message);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Day Book"
        description="Every POSTED accounting voucher for a date range — Kacchi Sale/Purchase Challans are included, since they are real accounting transactions."
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
            <div>
              <Label className="text-xs text-muted-foreground">Voucher Type</Label>
              <Select value={voucherType || ALL} onValueChange={(v) => setVoucherType(v === ALL ? '' : (v as VoucherType))}>
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All Types</SelectItem>
                  {VOUCHER_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>{t}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button type="button" onClick={load}>Apply</Button>
          </div>
        }
      />

      {!loading && report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Vouchers</p>
              <p className="text-lg font-bold">{report.rows.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Debit</p>
              <p className="text-lg font-bold">{money(report.totalDebit)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Credit</p>
              <p className="text-lg font-bold">{money(report.totalCredit)}</p>
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
                <TableHead>Voucher Type</TableHead>
                <TableHead>Voucher Number</TableHead>
                <TableHead>Narration</TableHead>
                <TableHead className="text-right">Debit</TableHead>
                <TableHead className="text-right">Credit</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {report?.rows.map((r) => (
                  <TableRow key={r.journalId} className="cursor-pointer" onClick={() => openJournal(r.journalId)}>
                    <TableCell>{r.journalDate}</TableCell>
                    <TableCell className="font-medium">{r.voucherType}</TableCell>
                    <TableCell>{r.voucherNumber || r.journalNumber}</TableCell>
                    <TableCell className="max-w-xs truncate text-muted-foreground">{r.narration || '—'}</TableCell>
                    <TableCell className="text-right">{r.debit > 0 ? money(r.debit) : '—'}</TableCell>
                    <TableCell className="text-right">{r.credit > 0 ? money(r.credit) : '—'}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && (report?.rows.length ?? 0) === 0 && (
            <EmptyState icon={BookText} title="No vouchers found" description="No posted accounting vouchers in this range." />
          )}
        </CardContent>
      </Card>

      <Dialog open={!!viewJournal} onOpenChange={(open) => !open && setViewJournal(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Journal {viewJournal?.journalNumber}</DialogTitle>
          </DialogHeader>
          {viewJournal && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                <div>
                  <p className="text-muted-foreground">Date</p>
                  <p className="font-medium">{viewJournal.journalDate}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Voucher</p>
                  <p className="font-medium">{viewJournal.voucherType} {viewJournal.voucherNumber ? `· ${viewJournal.voucherNumber}` : ''}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Status</p>
                  <Badge variant={statusVariant(viewJournal.status)}>{viewJournal.status}</Badge>
                </div>
              </div>
              {viewJournal.narration && <p className="text-sm text-muted-foreground">{viewJournal.narration}</p>}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Account</TableHead>
                    <TableHead className="text-right">Debit</TableHead>
                    <TableHead className="text-right">Credit</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {viewJournal.lines.map((line) => (
                    <TableRow key={line.id}>
                      <TableCell>
                        <span className="font-medium">{line.accountName}</span>
                        <span className="block text-xs text-muted-foreground">{line.accountCode}</span>
                      </TableCell>
                      <TableCell className="text-right">{line.debitAmount > 0 ? money(line.debitAmount) : '—'}</TableCell>
                      <TableCell className="text-right">{line.creditAmount > 0 ? money(line.creditAmount) : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex justify-end gap-6 border-t border-border pt-3 text-sm font-semibold">
                <span>Total Debit: {money(viewJournal.totalDebit)}</span>
                <span>Total Credit: {money(viewJournal.totalCredit)}</span>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
