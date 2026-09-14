import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { FileSpreadsheet } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { Gstr1Response, GstTransactionRow } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { money, firstDayOfMonthIso, todayIso } from './gstFormat';

function TransactionsTable({ rows }: { rows: GstTransactionRow[] }) {
  if (rows.length === 0) {
    return <EmptyState icon={FileSpreadsheet} title="No transactions" description="Nothing in this section for the selected filters." />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Voucher</TableHead>
          <TableHead>Date</TableHead>
          <TableHead>Party</TableHead>
          <TableHead>GSTIN</TableHead>
          <TableHead>Place of Supply</TableHead>
          <TableHead className="text-right">Taxable</TableHead>
          <TableHead className="text-right">CGST</TableHead>
          <TableHead className="text-right">SGST</TableHead>
          <TableHead className="text-right">IGST</TableHead>
          <TableHead className="text-right">Total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.gstTransactionId}>
            <TableCell className="font-medium">{r.voucherNumber}</TableCell>
            <TableCell>{r.voucherDate}</TableCell>
            <TableCell>{r.partyName ?? '—'}</TableCell>
            <TableCell className="font-mono text-xs">{r.partyGstin ?? '—'}</TableCell>
            <TableCell>{r.placeOfSupplyStateCode ?? '—'}</TableCell>
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

export default function Gstr1Report() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<Gstr1Response | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (from: string, to: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.gstr1({ fromDate: from, toDate: to });
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load GSTR-1').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(fromDate, toDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const applyFilters = () => load(fromDate, toDate);

  return (
    <div className="space-y-6">
      <BackButton label="Back to GST Reports" onClick={() => navigate('/gst-reports')} />

      <PageHeader
        title="GSTR-1"
        description="Outward supplies — B2B, B2C, and HSN-wise summary. Excludes Kacchi Sale Challans and draft bills entirely."
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
              <p className="text-xs text-muted-foreground">Total Value</p>
              <p className="text-lg font-bold">{money(report.totals.totalValue)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Invoices</p>
              <p className="text-lg font-bold">{report.totals.transactionCount}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-5">
          <Tabs defaultValue="b2b">
            <TabsList>
              <TabsTrigger value="b2b">B2B ({report?.b2bTransactions.length ?? 0})</TabsTrigger>
              <TabsTrigger value="b2c">B2C ({report?.b2cTransactions.length ?? 0})</TabsTrigger>
              <TabsTrigger value="hsn">HSN Summary ({report?.hsnSummary.length ?? 0})</TabsTrigger>
              <TabsTrigger value="cdn">Credit/Debit Notes</TabsTrigger>
            </TabsList>

            {loading ? (
              <div className="mt-4 overflow-hidden rounded-md border border-border">
                <Table>
                  <TableSkeleton columns={6} />
                </Table>
              </div>
            ) : (
              <>
                <TabsContent value="b2b">
                  <TransactionsTable rows={report?.b2bTransactions ?? []} />
                </TabsContent>
                <TabsContent value="b2c">
                  <TransactionsTable rows={report?.b2cTransactions ?? []} />
                </TabsContent>
                <TabsContent value="hsn">
                  {(report?.hsnSummary.length ?? 0) === 0 ? (
                    <EmptyState icon={FileSpreadsheet} title="No HSN data" description="No outward supplies in this range." />
                  ) : (
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
                        {report?.hsnSummary.map((h) => (
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
                  )}
                </TabsContent>
                <TabsContent value="cdn">
                  <div className="flex items-center gap-2 py-6">
                    <Badge variant="muted">Not applicable</Badge>
                    <p className="text-sm text-muted-foreground">
                      Credit Notes and Debit Notes are not implemented anywhere in StoreHub yet — this section is structurally
                      present for GSTR-1 completeness but always empty.
                    </p>
                  </div>
                </TabsContent>
              </>
            )}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
