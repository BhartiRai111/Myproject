import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ShoppingBag } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { PurchaseGstReportResponse } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Pagination } from '@/components/ui/pagination';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './gstFormat';

const PAGE_SIZE = 20;

export default function PurchaseGstReport() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [page, setPage] = useState(0);
  const [report, setReport] = useState<PurchaseGstReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string, p: number) => {
    setLoading(true);
    try {
      const res = await gstReportApi.purchaseGstReport({ fromDate: from, toDate: to, page: p, size: PAGE_SIZE });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Purchase GST Report').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(fromDate, toDate, page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const applyFilters = () => {
    setPage(0);
    load(fromDate, toDate, 0);
  };

  const rows = report?.transactions.content ?? [];

  return (
    <div className="space-y-6">
      <BackButton label="Back to GST Reports" onClick={() => navigate('/gst-reports')} />

      <PageHeader
        title="Purchase GST Report"
        description="Every GST-reportable posted purchase, with Input Tax Credit (ITC) eligibility per row. Excludes Kacchi Purchase Challans and draft bills entirely."
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
            <Button type="button" onClick={applyFilters}>
              Apply
            </Button>
          </div>
        }
      />

      {!loading && report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Taxable Value</p>
              <p className="text-lg font-bold">{money(report.totals.taxableAmount)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Tax</p>
              <p className="text-lg font-bold">{money(report.totals.totalTax)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">ITC-Eligible Tax</p>
              <p className="text-lg font-bold text-success">{money(report.eligibleItcTotal)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Bills</p>
              <p className="text-lg font-bold">{report.totals.transactionCount}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Voucher</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Supplier</TableHead>
                <TableHead>GSTIN</TableHead>
                <TableHead className="text-right">Taxable</TableHead>
                <TableHead className="text-right">Total Tax</TableHead>
                <TableHead className="text-right">Total Value</TableHead>
                <TableHead>ITC Status</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.gstTransactionId}>
                    <TableCell className="font-medium">{r.voucherNumber}</TableCell>
                    <TableCell>{r.voucherDate}</TableCell>
                    <TableCell>{r.partyName ?? '—'}</TableCell>
                    <TableCell className="font-mono text-xs">{r.partyGstin ?? '—'}</TableCell>
                    <TableCell className="text-right">{money(r.taxableAmount)}</TableCell>
                    <TableCell className="text-right">{money(r.totalTax)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(r.totalValue)}</TableCell>
                    <TableCell>
                      {r.itcEligible ? (
                        <Badge variant="success">ITC Eligible</Badge>
                      ) : (
                        <Badge variant="muted">Not Eligible (no valid GSTIN)</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && rows.length === 0 && (
            <EmptyState icon={ShoppingBag} title="No purchases found" description="No GST-reportable purchases in this range." />
          )}

          {!loading && report && report.transactions.totalPages > 1 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={report.transactions.totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
