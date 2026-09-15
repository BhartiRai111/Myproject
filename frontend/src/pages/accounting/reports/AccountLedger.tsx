import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { BookOpen } from 'lucide-react';
import { accountApi, accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { Account, AccountLedgerResponse } from '../../../types/accounting';
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

export default function AccountLedger() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<AccountLedgerResponse | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    accountApi.list({ active: true, page: 0, size: 200 }).then((res) => {
      setAccounts(res.data.content);
      if (res.data.content.length > 0) {
        setAccountId(res.data.content[0].id);
      }
    });
  }, []);

  const load = async (id: number) => {
    setLoading(true);
    try {
      const res = await accountingReportApi.accountLedger(id, fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Account Ledger').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (accountId) load(accountId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId]);

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Account Ledger"
        description="Running-balance statement for one account, computed live from posted journal entries."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label className="text-xs text-muted-foreground">Account</Label>
              <Select value={accountId ? String(accountId) : undefined} onValueChange={(v) => setAccountId(Number(v))}>
                <SelectTrigger className="w-56">
                  <SelectValue placeholder="Select account" />
                </SelectTrigger>
                <SelectContent>
                  {accounts.map((a) => (
                    <SelectItem key={a.id} value={String(a.id)}>{a.accountCode} — {a.accountName}</SelectItem>
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
            <Button type="button" onClick={() => accountId && load(accountId)}>Apply</Button>
          </div>
        }
      />

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
                {report?.openingBalance !== undefined && (
                  <TableRow className="bg-muted/40">
                    <TableCell colSpan={6} className="font-medium">Opening Balance</TableCell>
                    <TableCell className="text-right font-medium">{money(report.openingBalance)} {report.openingBalanceType}</TableCell>
                  </TableRow>
                )}
                {report?.rows.map((r, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{r.journalDate}</TableCell>
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
            <EmptyState icon={BookOpen} title="No activity" description="No postings for this account in this range." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
