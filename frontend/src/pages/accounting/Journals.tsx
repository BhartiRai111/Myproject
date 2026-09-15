import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { BookText, Eye, MoreHorizontal, Plus, Search } from 'lucide-react';
import { journalApi } from '../../api/accountingApi';
import { parseApiError } from '../../utils/apiError';
import { JournalHeader, JournalStatus, VoucherType } from '../../types/accounting';
import { useAuth } from '@/context/AuthContext';
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
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const PAGE_SIZE = 20;
const ALL = '__all__';

const VOUCHER_TYPES: VoucherType[] = ['SALE', 'PURCHASE', 'RECEIPT', 'PAYMENT', 'JOURNAL', 'CREDIT_NOTE', 'DEBIT_NOTE'];
const JOURNAL_STATUSES: JournalStatus[] = ['DRAFT', 'POSTED', 'REVERSED'];

const money = (n: number) => (n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const statusVariant = (status: string) => {
  if (status === 'POSTED') return 'success';
  if (status === 'REVERSED') return 'muted';
  return 'warning';
};

export default function Journals() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [journals, setJournals] = useState<JournalHeader[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [voucherType, setVoucherType] = useState<VoucherType | ''>('');
  const [status, setStatus] = useState<JournalStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [viewJournal, setViewJournal] = useState<JournalHeader | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await journalApi.list({ search, voucherType: voucherType || undefined, status: status || undefined, page, size: PAGE_SIZE });
      setJournals(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load journals').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, voucherType, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const openView = async (journal: JournalHeader) => {
    try {
      const res = await journalApi.getById(journal.id);
      setViewJournal(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load journal details').message);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Journals"
        description="Every balanced double-entry posting made by the accounting engine."
        actions={
          isAdmin ? (
            <Button onClick={() => navigate('/accounting/journals/new')}>
              <Plus className="h-4 w-4" /> New Journal Entry
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by journal number or voucher number"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={voucherType || ALL}
              onValueChange={(v) => {
                setPage(0);
                setVoucherType(v === ALL ? '' : (v as VoucherType));
              }}
            >
              <SelectTrigger className="w-full sm:w-44">
                <SelectValue placeholder="Voucher Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Types</SelectItem>
                {VOUCHER_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={status || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatus(v === ALL ? '' : (v as JournalStatus));
              }}
            >
              <SelectTrigger className="w-full sm:w-40">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Statuses</SelectItem>
                {JOURNAL_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s}
                  </SelectItem>
                ))}
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
                <TableHead>Journal No.</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Voucher</TableHead>
                <TableHead>Narration</TableHead>
                <TableHead>Debit</TableHead>
                <TableHead>Credit</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created By</TableHead>
                <TableHead>Posted By</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={10} />
            ) : (
              <TableBody>
                {journals.map((j) => (
                  <TableRow key={j.id}>
                    <TableCell className="font-mono text-sm">{j.journalNumber}</TableCell>
                    <TableCell>{new Date(j.journalDate).toLocaleDateString('en-IN')}</TableCell>
                    <TableCell>
                      <span className="font-medium">{j.voucherType}</span>
                      {j.voucherNumber && <span className="text-muted-foreground"> · {j.voucherNumber}</span>}
                    </TableCell>
                    <TableCell className="max-w-xs truncate text-muted-foreground">{j.narration || '—'}</TableCell>
                    <TableCell>₹{money(j.totalDebit)}</TableCell>
                    <TableCell>₹{money(j.totalCredit)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(j.status)}>{j.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{j.createdBy || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{j.postedBy || '—'}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => openView(j)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && journals.length === 0 && (
            <EmptyState icon={BookText} title="No journals found" description="Post a Sale, Purchase, Receipt, or Payment to see it here." />
          )}

          {!loading && journals.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!viewJournal} onOpenChange={(open) => !open && setViewJournal(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Journal {viewJournal?.journalNumber}</DialogTitle>
          </DialogHeader>
          {viewJournal && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                <div>
                  <p className="text-muted-foreground">Date</p>
                  <p className="font-medium">{new Date(viewJournal.journalDate).toLocaleDateString('en-IN')}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Voucher</p>
                  <p className="font-medium">
                    {viewJournal.voucherType} {viewJournal.voucherNumber ? `· ${viewJournal.voucherNumber}` : ''}
                  </p>
                </div>
                <div>
                  <p className="text-muted-foreground">Status</p>
                  <Badge variant={statusVariant(viewJournal.status)}>{viewJournal.status}</Badge>
                </div>
              </div>
              {viewJournal.narration && (
                <p className="text-sm text-muted-foreground">{viewJournal.narration}</p>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Account</TableHead>
                    <TableHead>Narration</TableHead>
                    <TableHead className="text-right">Debit</TableHead>
                    <TableHead className="text-right">Credit</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {viewJournal.lines.map((line) => (
                    <TableRow key={line.id}>
                      <TableCell>
                        <span className="font-medium">{line.accountName}</span>
                        <span className="block text-xs text-muted-foreground">{line.accountCode}</span>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{line.narration || '—'}</TableCell>
                      <TableCell className="text-right">{line.debitAmount > 0 ? `₹${money(line.debitAmount)}` : '—'}</TableCell>
                      <TableCell className="text-right">{line.creditAmount > 0 ? `₹${money(line.creditAmount)}` : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex justify-end gap-6 border-t border-border pt-3 text-sm font-semibold">
                <span>Total Debit: ₹{money(viewJournal.totalDebit)}</span>
                <span>Total Credit: ₹{money(viewJournal.totalCredit)}</span>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
