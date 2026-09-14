import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Hash } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { HsnSummaryReportResponse, HsnSummaryRow } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { money, firstDayOfMonthIso, todayIso } from './gstFormat';

function HsnTable({ rows }: { rows: HsnSummaryRow[] }) {
  if (rows.length === 0) {
    return <EmptyState icon={Hash} title="No HSN data" description="No line items in this range." />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>HSN Code</TableHead>
          <TableHead>Description</TableHead>
          <TableHead>Unit</TableHead>
          <TableHead className="text-right">Qty</TableHead>
          <TableHead className="text-right">Taxable</TableHead>
          <TableHead className="text-right">CGST</TableHead>
          <TableHead className="text-right">SGST</TableHead>
          <TableHead className="text-right">IGST</TableHead>
          <TableHead className="text-right">Total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((h) => (
          <TableRow key={h.hsnCode}>
            <TableCell className="font-mono">{h.hsnCode}</TableCell>
            <TableCell>{h.description || '—'}</TableCell>
            <TableCell>{h.unit || '—'}</TableCell>
            <TableCell className="text-right">{h.totalQuantity}</TableCell>
            <TableCell className="text-right">{money(h.taxableAmount)}</TableCell>
            <TableCell className="text-right">{money(h.cgstAmount)}</TableCell>
            <TableCell className="text-right">{money(h.sgstAmount)}</TableCell>
            <TableCell className="text-right">{money(h.igstAmount)}</TableCell>
            <TableCell className="text-right font-semibold">{money(h.totalValue)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export default function HsnSummaryReport() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<HsnSummaryReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.hsnSummary({ fromDate: from, toDate: to });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load HSN Summary').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(fromDate, toDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-6">
      <BackButton label="Back to GST Reports" onClick={() => navigate('/gst-reports')} />

      <PageHeader
        title="HSN Summary"
        description="Outward (sales) and inward (purchases) supplies aggregated by HSN code, at line-item level."
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

      <Card>
        <CardContent className="p-5">
          <Tabs defaultValue="outward">
            <TabsList>
              <TabsTrigger value="outward">Outward (Sales)</TabsTrigger>
              <TabsTrigger value="inward">Inward (Purchases)</TabsTrigger>
            </TabsList>
            {loading ? (
              <div className="mt-4 overflow-hidden rounded-md border border-border">
                <Table>
                  <TableSkeleton columns={9} />
                </Table>
              </div>
            ) : (
              <>
                <TabsContent value="outward">
                  <HsnTable rows={report?.outward ?? []} />
                </TabsContent>
                <TabsContent value="inward">
                  <HsnTable rows={report?.inward ?? []} />
                </TabsContent>
              </>
            )}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
