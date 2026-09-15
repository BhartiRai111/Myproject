import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { TrendingDown } from 'lucide-react';
import { parseApiError } from '../../../utils/apiError';
import { ExpenseIncomeSummaryResponse } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';
import type { AxiosResponse } from 'axios';

interface Props {
  title: string;
  description: string;
  totalLabel: string;
  fetcher: (fromDate?: string, toDate?: string) => Promise<AxiosResponse<ExpenseIncomeSummaryResponse>>;
}

export default function ExpenseIncomeSummaryView({ title, description, totalLabel, fetcher }: Props) {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<ExpenseIncomeSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetcher(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, `Failed to load ${title}`).message);
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
        title={title}
        description={description}
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

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <Skeleton key={i} className="h-24 w-full" />)}
        </div>
      ) : (report?.accounts.length ?? 0) === 0 ? (
        <EmptyState icon={TrendingDown} title="No activity" description="No activity in this range." />
      ) : (
        <>
          <div className="space-y-4">
            {report?.accounts.map((group) => (
              <Card key={group.accountId}>
                <CardContent className="p-0">
                  <div className="flex items-center justify-between border-b border-border p-4">
                    <div>
                      <p className="font-semibold">{group.accountName}</p>
                      <p className="text-xs text-muted-foreground">{group.accountCode}</p>
                    </div>
                    <p className="text-lg font-bold">{money(group.total)}</p>
                  </div>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Date</TableHead>
                        <TableHead>Voucher</TableHead>
                        <TableHead>Narration</TableHead>
                        <TableHead className="text-right">Amount</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {group.vouchers.map((v) => (
                        <TableRow key={v.journalId}>
                          <TableCell>{v.voucherDate}</TableCell>
                          <TableCell>{v.voucherType}{v.voucherNumber ? ` · ${v.voucherNumber}` : ''}</TableCell>
                          <TableCell className="text-muted-foreground">{v.narration || '—'}</TableCell>
                          <TableCell className="text-right">{money(v.amount)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card>
            <CardContent className="flex items-center justify-between p-5">
              <span className="text-base font-semibold">{totalLabel}</span>
              <span className="text-xl font-bold">{money(report?.totalAmount)}</span>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
