import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CalendarCheck } from 'lucide-react';
import { dayClosingApi } from '../../api/dayClosingApi';
import { parseApiError } from '../../utils/apiError';
import { DayClosingSummary } from '../../types/dayClosing';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const money = (n?: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export default function DayClosingPage() {
  const navigate = useNavigate();
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [summary, setSummary] = useState<DayClosingSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [actualCash, setActualCash] = useState('');
  const [differenceReason, setDifferenceReason] = useState('');
  const [error, setError] = useState('');
  const [closing, setClosing] = useState(false);
  const [closeConfirm, setCloseConfirm] = useState(false);

  const [history, setHistory] = useState<DayClosingSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  const loadSummary = async (d: string) => {
    setLoading(true);
    try {
      const res = await dayClosingApi.summary(d);
      setSummary(res.data);
      setActualCash(res.data.closed ? String(res.data.actualCash ?? '') : '');
      setDifferenceReason(res.data.differenceReason || '');
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load day closing summary').message);
    } finally {
      setLoading(false);
    }
  };

  const loadHistory = async () => {
    setHistoryLoading(true);
    try {
      const res = await dayClosingApi.history();
      setHistory(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load day closing history').message);
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    loadSummary(date);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  useEffect(() => {
    loadHistory();
  }, []);

  const difference = summary && actualCash !== '' ? Number(actualCash) - summary.expectedCash : 0;
  const differenceRequiresReason = summary && actualCash !== '' && Math.abs(difference) > 0.001;

  const handleClose = async () => {
    if (!summary) return;
    setClosing(true);
    try {
      await dayClosingApi.close({
        closingDate: date,
        actualCash: Number(actualCash),
        differenceReason: differenceRequiresReason ? differenceReason.trim() || undefined : undefined,
      });
      toast.success(`Day ${date} closed`);
      setCloseConfirm(false);
      loadSummary(date);
      loadHistory();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to close the day').message);
      setCloseConfirm(false);
    } finally {
      setClosing(false);
    }
  };

  const requestClose = () => {
    setError('');
    if (actualCash === '' || Number.isNaN(Number(actualCash))) {
      setError('Enter the actual cash counted');
      return;
    }
    if (differenceRequiresReason && !differenceReason.trim()) {
      setError('A reason is required when actual cash differs from expected cash');
      return;
    }
    setCloseConfirm(true);
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Day Closing"
        description="Summarize sales, purchases, cash by mode, receipts, payments, and expenses for a date — then reconcile expected vs. actual cash."
      />

      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="max-w-xs space-y-1.5">
            <Label>Date</Label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} max={new Date().toISOString().slice(0, 10)} />
          </div>

          {loading || !summary ? (
            <Skeleton className="h-64 w-full" />
          ) : (
            <>
              {summary.closed && (
                <Alert>
                  <AlertDescription>
                    This day was closed by {summary.closedBy} on {summary.closedAt?.slice(0, 16).replace('T', ' ')}.
                  </AlertDescription>
                </Alert>
              )}

              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Total Sales</p>
                  <p className="text-lg font-semibold">{money(summary.totalSales)}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Total Purchases</p>
                  <p className="text-lg font-semibold">{money(summary.totalPurchases)}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Total Expenses</p>
                  <p className="text-lg font-semibold">{money(summary.totalExpenses)}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Credit Sales</p>
                  <p className="text-lg font-semibold">{money(summary.creditSales)}</p>
                </div>
              </div>

              <Separator />

              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div className="space-y-1.5 text-sm">
                  <p className="font-medium">Sales by Mode</p>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Cash Sales</span>
                    <span>{money(summary.cashSales)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Card Sales</span>
                    <span>{money(summary.cardSales)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>UPI Sales</span>
                    <span>{money(summary.upiSales)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Credit Sales</span>
                    <span>{money(summary.creditSales)}</span>
                  </div>
                </div>
                <div className="space-y-1.5 text-sm">
                  <p className="font-medium">Cash Movements</p>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Receipts</span>
                    <span>{money(summary.totalReceipts)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Payments</span>
                    <span>{money(summary.totalPayments)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Cash In</span>
                    <span>{money(summary.cashIn)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Cash Out</span>
                    <span>{money(summary.cashOut)}</span>
                  </div>
                </div>
              </div>

              <Separator />

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Expected Cash</p>
                  <p className="text-lg font-semibold">{money(summary.expectedCash)}</p>
                </div>
                <div className="space-y-1.5">
                  <Label>Actual Cash Counted</Label>
                  <Input
                    type="number"
                    step="0.01"
                    value={actualCash}
                    onChange={(e) => setActualCash(e.target.value)}
                    disabled={summary.closed}
                  />
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Difference</p>
                  <p className={`text-lg font-semibold ${Math.abs(difference) > 0.001 ? 'text-destructive' : ''}`}>
                    {money(actualCash !== '' ? difference : summary.difference)}
                  </p>
                </div>
              </div>

              {!summary.closed && differenceRequiresReason && (
                <div className="space-y-1.5">
                  <Label>Difference Reason (required)</Label>
                  <Textarea
                    value={differenceReason}
                    onChange={(e) => setDifferenceReason(e.target.value)}
                    rows={2}
                    placeholder="Explain the cash difference — never silently adjust accounting balances."
                  />
                </div>
              )}

              {summary.closed && summary.differenceReason && (
                <div className="space-y-1.5 text-sm">
                  <p className="text-xs text-muted-foreground">Difference Reason</p>
                  <p>{summary.differenceReason}</p>
                </div>
              )}

              {error && (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              {!summary.closed && (
                <div className="flex justify-end">
                  <Button onClick={requestClose} loading={closing}>
                    Close Day
                  </Button>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <div className="border-b border-border p-4">
            <p className="font-semibold">Closing History</p>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Date</TableHead>
                <TableHead>Expected Cash</TableHead>
                <TableHead>Actual Cash</TableHead>
                <TableHead>Difference</TableHead>
                <TableHead>Closed By</TableHead>
              </TableRow>
            </TableHeader>
            {historyLoading ? (
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i}>
                    <TableCell colSpan={5}>
                      <Skeleton className="h-6 w-full" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            ) : (
              <TableBody>
                {history.map((h) => (
                  <TableRow key={h.closingDate} className="cursor-pointer" onClick={() => setDate(h.closingDate)}>
                    <TableCell className="font-medium">{h.closingDate}</TableCell>
                    <TableCell>{money(h.expectedCash)}</TableCell>
                    <TableCell>{money(h.actualCash)}</TableCell>
                    <TableCell>
                      {Math.abs(h.difference ?? 0) > 0.001 ? (
                        <Badge variant="destructive">{money(h.difference)}</Badge>
                      ) : (
                        <Badge variant="success">Matched</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{h.closedBy || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!historyLoading && history.length === 0 && (
            <div className="p-8 text-center text-sm text-muted-foreground">
              <CalendarCheck className="mx-auto mb-2 h-8 w-8 opacity-50" />
              No days have been closed yet.
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={closeConfirm}
        title={`Close ${date}?`}
        description="This records the day's expected vs. actual cash. It does not modify accounting balances. This cannot be undone — a closed day cannot be re-closed."
        confirmLabel={closing ? 'Closing...' : 'Close Day'}
        cancelLabel="Cancel"
        destructive={false}
        onConfirm={handleClose}
        onCancel={() => setCloseConfirm(false)}
      />
    </div>
  );
}
