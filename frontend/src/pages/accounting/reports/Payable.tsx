import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Truck } from 'lucide-react';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { ReceivablePayableResponse } from '../../../types/accounting';
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

export default function Payable() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<ReceivablePayableResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.payable(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Payable').message);
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
        title="Payable"
        description="Outstanding supplier balances: opening + purchases − payments, from the accounting journal's supplier control account."
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
              <p className="text-xs text-muted-foreground">Opening</p>
              <p className="text-lg font-bold">{money(report.totalOpening)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Purchases</p>
              <p className="text-lg font-bold">{money(report.totalTransactions)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Payments</p>
              <p className="text-lg font-bold">{money(report.totalPayments)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Payable</p>
              <p className="text-lg font-bold">{money(report.totalOutstanding)}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Supplier</TableHead>
                <TableHead className="text-right">Opening</TableHead>
                <TableHead className="text-right">Purchases</TableHead>
                <TableHead className="text-right">Payments</TableHead>
                <TableHead className="text-right">Credit Notes</TableHead>
                <TableHead className="text-right">Debit Notes</TableHead>
                <TableHead className="text-right">Payable</TableHead>
                <TableHead className="text-right">Ledger</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {report?.rows.map((r) => (
                  <TableRow key={r.partyId}>
                    <TableCell className="font-medium">{r.partyName}</TableCell>
                    <TableCell className="text-right">{money(r.openingBalance)}</TableCell>
                    <TableCell className="text-right">{money(r.transactionAmount)}</TableCell>
                    <TableCell className="text-right">{money(r.paymentAmount)}</TableCell>
                    <TableCell className="text-right text-muted-foreground">{money(r.creditNoteAmount)}</TableCell>
                    <TableCell className="text-right text-muted-foreground">{money(r.debitNoteAmount)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(r.closingOutstanding)}</TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate(`/accounting/party-ledger?partyType=SUPPLIER&partyId=${r.partyId}`)}
                      >
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && (report?.rows.length ?? 0) === 0 && (
            <EmptyState icon={Truck} title="Nothing payable" description="No supplier balances in this range." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
