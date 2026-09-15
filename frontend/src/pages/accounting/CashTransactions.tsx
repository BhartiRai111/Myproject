import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Search, Plus, Wallet } from 'lucide-react';
import { cashTransactionApi } from '../../api/cashTransactionApi';
import { parseApiError } from '../../utils/apiError';
import { CashTransaction, CashTransactionStatus, CashTransactionType } from '../../types/cashTransaction';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const PAGE_SIZE = 10;
const ALL = '__all__';
const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function statusVariant(status: CashTransactionStatus) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

function typeVariant(type: CashTransactionType) {
  return type === 'CASH_IN' ? ('success' as const) : ('warning' as const);
}

export default function CashTransactions() {
  const navigate = useNavigate();
  const [txns, setTxns] = useState<CashTransaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [type, setType] = useState<CashTransactionType | ''>('');
  const [status, setStatus] = useState<CashTransactionStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = async () => {
    setLoading(true);
    try {
      const res = await cashTransactionApi.list({
        search,
        transactionType: type || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE,
      });
      setTxns(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load cash transactions').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, type, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Cash Management"
        description="Cash In / Cash Out entries — posted through the same accounting engine as every other voucher. Reflected directly in the Cash Book."
        actions={
          <Button onClick={() => navigate('/accounting/cash-transactions/new')}>
            <Plus className="h-4 w-4" /> New Cash Entry
          </Button>
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by transaction number or reason"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={type || ALL}
              onValueChange={(v) => {
                setPage(0);
                setType(v === ALL ? '' : (v as CashTransactionType));
              }}
            >
              <SelectTrigger className="w-full sm:w-40">
                <SelectValue placeholder="Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Types</SelectItem>
                <SelectItem value="CASH_IN">Cash In</SelectItem>
                <SelectItem value="CASH_OUT">Cash Out</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={status || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatus(v === ALL ? '' : (v as CashTransactionStatus));
              }}
            >
              <SelectTrigger className="w-full sm:w-40">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Statuses</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="POSTED">Posted</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
              </SelectContent>
            </Select>
            <Button type="submit" variant="secondary" className="sm:w-auto">
              Search
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Transaction Number</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Payment Mode</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {txns.map((t) => (
                  <TableRow key={t.id} className="cursor-pointer" onClick={() => navigate(`/accounting/cash-transactions/${t.id}`)}>
                    <TableCell className="font-mono text-sm font-medium">{t.transactionNumber}</TableCell>
                    <TableCell>{t.transactionDate}</TableCell>
                    <TableCell>
                      <Badge variant={typeVariant(t.transactionType)}>{t.transactionType === 'CASH_IN' ? 'Cash In' : 'Cash Out'}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{t.paymentMode}</TableCell>
                    <TableCell className="text-muted-foreground">{t.reason}</TableCell>
                    <TableCell>{money(t.amount)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && txns.length === 0 && (
            <EmptyState icon={Wallet} title="No cash transactions found" description="Record a Cash In or Cash Out entry to get started." />
          )}

          {!loading && txns.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
