import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GitCompareArrows } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { ReconciliationResponse, ReconciliationStatus } from '../../types/gstReport';
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
import { money, firstDayOfMonthIso, todayIso } from './gstFormat';

function statusVariant(status: ReconciliationStatus) {
  if (status === 'MATCHED') return 'success' as const;
  if (status === 'MISMATCHED') return 'warning' as const;
  if (status === 'MISSING') return 'destructive' as const;
  return 'destructive' as const;
}

export default function GstReconciliation() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<ReconciliationResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.reconciliation({ fromDate: from, toDate: to });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load GST Reconciliation').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(fromDate, toDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rows = report?.rows ?? [];

  return (
    <div className="space-y-6">
      <BackButton label="Back to GST Reports" onClick={() => navigate('/gst-reports')} />

      <PageHeader
        title="GST Reconciliation"
        description="Compares every eligible posted Sale/Purchase against the GST reporting dataset it should have synced to."
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
            <Button type="button" onClick={() => load(fromDate, toDate)}>
              Apply
            </Button>
          </div>
        }
      />

      {!loading && report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Matched</p>
              <p className="text-lg font-bold text-success">{report.matchedCount}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Mismatched</p>
              <p className="text-lg font-bold text-warning">{report.mismatchedCount}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Missing</p>
              <p className="text-lg font-bold text-destructive">{report.missingCount}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Duplicate</p>
              <p className="text-lg font-bold text-destructive">{report.duplicateCount}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Voucher</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Source Taxable</TableHead>
                <TableHead className="text-right">Reported Taxable</TableHead>
                <TableHead className="text-right">Source Tax</TableHead>
                <TableHead className="text-right">Reported Tax</TableHead>
                <TableHead>Remarks</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={9} />
            ) : (
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={`${r.sourceTransactionType}-${r.sourceTransactionId}`}>
                    <TableCell>{r.sourceTransactionType}</TableCell>
                    <TableCell className="font-medium">{r.voucherNumber}</TableCell>
                    <TableCell>{r.voucherDate}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">{money(r.sourceTaxableAmount)}</TableCell>
                    <TableCell className="text-right">{r.reportedTaxableAmount != null ? money(r.reportedTaxableAmount) : '—'}</TableCell>
                    <TableCell className="text-right">{money(r.sourceTotalTax)}</TableCell>
                    <TableCell className="text-right">{r.reportedTotalTax != null ? money(r.reportedTotalTax) : '—'}</TableCell>
                    <TableCell className="max-w-[16rem] text-xs text-muted-foreground">{r.remarks ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && rows.length === 0 && (
            <EmptyState icon={GitCompareArrows} title="Nothing to reconcile" description="No eligible transactions in this range." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
