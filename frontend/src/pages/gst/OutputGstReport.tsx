import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowUpRight } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { GstReportListResponse } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Pagination } from '@/components/ui/pagination';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './gstFormat';

const PAGE_SIZE = 20;

export default function OutputGstReport() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [page, setPage] = useState(0);
  const [report, setReport] = useState<GstReportListResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string, p: number) => {
    setLoading(true);
    try {
      const res = await gstReportApi.outputGst({ fromDate: from, toDate: to, page: p, size: PAGE_SIZE });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Output GST Report').message);
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
        title="Output GST Report"
        description="GST collected on every reportable sale — the output side of the GST ledger."
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
              <p className="text-xs text-muted-foreground">CGST</p>
              <p className="text-lg font-bold">{money(report.totals.cgstAmount)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">SGST</p>
              <p className="text-lg font-bold">{money(report.totals.sgstAmount)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">IGST</p>
              <p className="text-lg font-bold">{money(report.totals.igstAmount)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Output Tax</p>
              <p className="text-lg font-bold">{money(report.totals.totalTax)}</p>
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
                <TableHead>Customer</TableHead>
                <TableHead className="text-right">Taxable</TableHead>
                <TableHead className="text-right">CGST</TableHead>
                <TableHead className="text-right">SGST</TableHead>
                <TableHead className="text-right">IGST</TableHead>
                <TableHead className="text-right">Total</TableHead>
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
                    <TableCell className="text-right">{money(r.taxableAmount)}</TableCell>
                    <TableCell className="text-right">{money(r.cgstAmount)}</TableCell>
                    <TableCell className="text-right">{money(r.sgstAmount)}</TableCell>
                    <TableCell className="text-right">{money(r.igstAmount)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(r.totalValue)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && rows.length === 0 && (
            <EmptyState icon={ArrowUpRight} title="No sales found" description="No GST-reportable sales in this range." />
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
