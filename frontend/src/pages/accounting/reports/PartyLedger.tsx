import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Users } from 'lucide-react';
import { accountingReportApi } from '../../../api/accountingApi';
import { customerApi } from '../../../api/customerApi';
import { supplierApi } from '../../../api/supplierApi';
import { parseApiError } from '../../../utils/apiError';
import { AccountingPartyType, PartyLedgerResponse } from '../../../types/accounting';
import { Customer } from '../../../types/sale';
import { Supplier } from '../../../types/supplier';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

export default function PartyLedger() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [partyType, setPartyType] = useState<AccountingPartyType>((searchParams.get('partyType') as AccountingPartyType) || 'CUSTOMER');
  const [partyId, setPartyId] = useState<number | null>(searchParams.get('partyId') ? Number(searchParams.get('partyId')) : null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<PartyLedgerResponse | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    customerApi.list().then((res) => setCustomers(res.data));
    supplierApi.list({ size: 500 }).then((res) => setSuppliers(res.data.content));
  }, []);

  const load = async (type: AccountingPartyType, id: number) => {
    setLoading(true);
    try {
      const res = await accountingReportApi.partyLedger(type, id, fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Party Ledger').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (partyId) {
      load(partyType, partyId);
      setSearchParams({ partyType, partyId: String(partyId) }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [partyType, partyId]);

  const partyOptions = partyType === 'CUSTOMER'
    ? customers.map((c) => ({ id: c.id, name: `${c.firstName} ${c.lastName || ''}`.trim() }))
    : suppliers.map((s) => ({ id: s.id, name: s.name }));

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Party Ledger"
        description="Unified running-balance statement for a customer or supplier, built from the same accounting journal as the Account Ledger."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label className="text-xs text-muted-foreground">Party Type</Label>
              <Select value={partyType} onValueChange={(v) => { setPartyType(v as AccountingPartyType); setPartyId(null); }}>
                <SelectTrigger className="w-36">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CUSTOMER">Customer</SelectItem>
                  <SelectItem value="SUPPLIER">Supplier</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">{partyType === 'CUSTOMER' ? 'Customer' : 'Supplier'}</Label>
              <Select value={partyId ? String(partyId) : undefined} onValueChange={(v) => setPartyId(Number(v))}>
                <SelectTrigger className="w-56">
                  <SelectValue placeholder="Select party" />
                </SelectTrigger>
                <SelectContent>
                  {partyOptions.map((p) => (
                    <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label htmlFor="fromDate" className="text-xs text-muted-foreground">From</Label>
              <Input id="fromDate" type="date" className="w-40" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </div>
            <div>
              <Label htmlFor="toDate" className="text-xs text-muted-foreground">To</Label>
              <Input id="toDate" type="date" className="w-40" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </div>
            <Button type="button" onClick={() => partyId && load(partyType, partyId)}>Apply</Button>
          </div>
        }
      />

      {!partyId && (
        <EmptyState icon={Users} title="Select a party" description="Choose a customer or supplier above to view their ledger." />
      )}

      {partyId && (
        <>
          {report && (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-2">
              <Card>
                <CardContent className="p-4">
                  <p className="text-xs text-muted-foreground">Opening Balance</p>
                  <p className="text-lg font-bold">{money(report.openingBalance)} {report.openingBalanceType}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-4">
                  <p className="text-xs text-muted-foreground">Closing Balance</p>
                  <p className="text-lg font-bold">{money(report.closingBalance)} {report.closingBalanceType}</p>
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
                    <TableHead>Particulars</TableHead>
                    <TableHead className="text-right">Debit</TableHead>
                    <TableHead className="text-right">Credit</TableHead>
                    <TableHead className="text-right">Balance</TableHead>
                  </TableRow>
                </TableHeader>
                {loading ? (
                  <TableSkeleton columns={7} />
                ) : (
                  <TableBody>
                    {report?.rows.map((r, idx) => (
                      <TableRow key={idx}>
                        <TableCell>{r.voucherDate}</TableCell>
                        <TableCell>{r.voucherType}</TableCell>
                        <TableCell>{r.voucherNumber || '—'}</TableCell>
                        <TableCell className="text-muted-foreground">{r.particulars || '—'}</TableCell>
                        <TableCell className="text-right">{r.debit > 0 ? money(r.debit) : '—'}</TableCell>
                        <TableCell className="text-right">{r.credit > 0 ? money(r.credit) : '—'}</TableCell>
                        <TableCell className="text-right font-medium">{money(r.balance)} {r.balanceType}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>

              {!loading && (report?.rows.length ?? 0) === 0 && (
                <EmptyState icon={Users} title="No activity" description="No postings for this party in this range." />
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
