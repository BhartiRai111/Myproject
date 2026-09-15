import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Receipt } from 'lucide-react';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { OutstandingBillReportResponse } from '../../../types/accounting';
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { money, todayIso } from './reportFormat';

function bucketVariant(bucket: string) {
  if (bucket === '0-30 Days') return 'success' as const;
  if (bucket === '31-60 Days') return 'warning' as const;
  return 'destructive' as const;
}

function OutstandingTable({ report, loading }: { report: OutstandingBillReportResponse | null; loading: boolean }) {
  if (loading) {
    return (
      <div className="overflow-hidden rounded-md border border-border">
        <Table>
          <TableSkeleton columns={7} />
        </Table>
      </div>
    );
  }
  if (!report || report.rows.length === 0) {
    return <EmptyState icon={Receipt} title="Nothing outstanding" description="No outstanding bills as of this date." />;
  }
  return (
    <>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {report.ageingSummary.map((b) => (
          <Card key={b.bucket}>
            <CardContent className="p-3">
              <p className="text-xs text-muted-foreground">{b.bucket}</p>
              <p className="text-sm font-bold">{money(b.amount)}</p>
              <p className="text-xs text-muted-foreground">{b.count} bill(s)</p>
            </CardContent>
          </Card>
        ))}
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Party</TableHead>
            <TableHead>Invoice No.</TableHead>
            <TableHead>Invoice Date</TableHead>
            <TableHead className="text-right">Invoice Amount</TableHead>
            <TableHead className="text-right">Received/Paid</TableHead>
            <TableHead className="text-right">Outstanding</TableHead>
            <TableHead>Ageing</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {report.rows.map((r) => (
            <TableRow key={r.billId}>
              <TableCell className="font-medium">{r.partyName}</TableCell>
              <TableCell>{r.invoiceNumber}</TableCell>
              <TableCell>{r.invoiceDate}</TableCell>
              <TableCell className="text-right">{money(r.invoiceAmount)}</TableCell>
              <TableCell className="text-right">{money(r.receivedOrPaidAmount)}</TableCell>
              <TableCell className="text-right font-semibold">{money(r.outstanding)}</TableCell>
              <TableCell>
                <Badge variant={bucketVariant(r.ageingBucket)}>{r.ageingBucket} ({r.daysOutstanding}d)</Badge>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}

export default function Outstanding() {
  const navigate = useNavigate();
  const [asOfDate, setAsOfDate] = useState(todayIso());
  const [customerReport, setCustomerReport] = useState<OutstandingBillReportResponse | null>(null);
  const [supplierReport, setSupplierReport] = useState<OutstandingBillReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (date: string) => {
    setLoading(true);
    try {
      const [customers, suppliers] = await Promise.all([
        accountingReportApi.outstandingCustomers(date),
        accountingReportApi.outstandingSuppliers(date),
      ]);
      setCustomerReport(customers.data);
      setSupplierReport(suppliers.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Outstanding Bills').message);
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
        title="Outstanding Bills"
        description={`Bill-wise outstanding with ageing, computed from invoice date (${customerReport?.ageingBasis ?? 'INVOICE_DATE'} — no due-date field exists on any bill in this app).`}
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

      <Card>
        <CardContent className="p-5">
          <Tabs defaultValue="customers">
            <TabsList>
              <TabsTrigger value="customers">Customers ({customerReport?.rows.length ?? 0})</TabsTrigger>
              <TabsTrigger value="suppliers">Suppliers ({supplierReport?.rows.length ?? 0})</TabsTrigger>
            </TabsList>
            <TabsContent value="customers">
              <OutstandingTable report={customerReport} loading={loading} />
            </TabsContent>
            <TabsContent value="suppliers">
              <OutstandingTable report={supplierReport} loading={loading} />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
