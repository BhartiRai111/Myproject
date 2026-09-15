import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { AccountSummaryResponse, AccountType } from '../../../types/accounting';
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
import { BarChart3 } from 'lucide-react';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

const ALL = '__all__';
const ACCOUNT_TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'INCOME', 'EXPENSE'];

export default function AccountSummary() {
  const navigate = useNavigate();
  const [accountType, setAccountType] = useState<AccountType | ''>('');
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<AccountSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.accountSummary(accountType || undefined, fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Account Summary').message);
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
        title="Account Summary"
        description="Opening, period debit/credit, and closing balance for every account with activity."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label className="text-xs text-muted-foreground">Account Type</Label>
              <Select value={accountType || ALL} onValueChange={(v) => setAccountType(v === ALL ? '' : (v as AccountType))}>
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All Types</SelectItem>
                  {ACCOUNT_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>{t}</SelectItem>
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
            <Button type="button" onClick={load}>Apply</Button>
          </div>
        }
      />

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Account</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="text-right">Opening</TableHead>
                <TableHead className="text-right">Debit</TableHead>
                <TableHead className="text-right">Credit</TableHead>
                <TableHead className="text-right">Closing</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {report?.rows.map((r) => (
                  <TableRow key={r.accountId}>
                    <TableCell className="font-mono text-sm">{r.accountCode}</TableCell>
                    <TableCell className="font-medium">{r.accountName}</TableCell>
                    <TableCell className="text-muted-foreground">{r.accountType}</TableCell>
                    <TableCell className="text-right">{money(r.openingBalance)}</TableCell>
                    <TableCell className="text-right">{money(r.debit)}</TableCell>
                    <TableCell className="text-right">{money(r.credit)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(r.closingBalance)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && (report?.rows.length ?? 0) === 0 && (
            <EmptyState icon={BarChart3} title="No activity" description="No account activity in this range." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
