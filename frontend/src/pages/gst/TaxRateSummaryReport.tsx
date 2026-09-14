import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Percent } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { TaxRateSummaryReportResponse, TaxRateSummaryRow } from '../../types/gstReport';
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

function RateTable({ rows }: { rows: TaxRateSummaryRow[] }) {
  if (rows.length === 0) {
    return <EmptyState icon={Percent} title="No data" description="No line items in this range." />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>GST Rate</TableHead>
          <TableHead className="text-right">Taxable</TableHead>
          <TableHead className="text-right">CGST</TableHead>
          <TableHead className="text-right">SGST</TableHead>
          <TableHead className="text-right">IGST</TableHead>
          <TableHead className="text-right">Total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.gstPercent}>
            <TableCell className="font-medium">{r.gstPercent}%</TableCell>
            <TableCell className="text-right">{money(r.taxableAmount)}</TableCell>
            <TableCell className="text-right">{money(r.cgstAmount)}</TableCell>
            <TableCell className="text-right">{money(r.sgstAmount)}</TableCell>
            <TableCell className="text-right">{money(r.igstAmount)}</TableCell>
            <TableCell className="text-right font-semibold">{money(r.totalValue)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export default function TaxRateSummaryReport() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<TaxRateSummaryReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.taxRateSummary({ fromDate: from, toDate: to });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Tax Rate Summary').message);
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
        title="Tax Rate Summary"
        description="Outward and inward supplies aggregated by GST rate, taken from each line item's own rate — never hardcoded."
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
                  <TableSkeleton columns={6} />
                </Table>
              </div>
            ) : (
              <>
                <TabsContent value="outward">
                  <RateTable rows={report?.outward ?? []} />
                </TabsContent>
                <TabsContent value="inward">
                  <RateTable rows={report?.inward ?? []} />
                </TabsContent>
              </>
            )}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
