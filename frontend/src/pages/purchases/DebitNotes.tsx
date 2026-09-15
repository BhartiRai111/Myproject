import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Search, Plus, FilePlus2 } from 'lucide-react';
import { debitNoteApi } from '../../api/debitNoteApi';
import { parseApiError } from '../../utils/apiError';
import { DebitNote, NoteStatus } from '../../types/note';
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

function statusVariant(status: NoteStatus) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function DebitNotes() {
  const navigate = useNavigate();
  const [notes, setNotes] = useState<DebitNote[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<NoteStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = async () => {
    setLoading(true);
    try {
      const res = await debitNoteApi.list({ search, status: status || undefined, page, size: PAGE_SIZE });
      setNotes(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load debit notes').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Purchases" onClick={() => navigate('/purchases')} />

      <PageHeader
        title="Debit Notes"
        description="Purchase returns, supplier debits, and price/tax adjustments — linked to their source purchase."
        actions={
          <Button onClick={() => navigate('/purchases/debit-notes/new')}>
            <Plus className="h-4 w-4" /> New Debit Note
          </Button>
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by voucher number or supplier"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={status || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatus(v === ALL ? '' : (v as NoteStatus));
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
                <TableHead>Voucher Number</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Supplier</TableHead>
                <TableHead>Source Purchase</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {notes.map((n) => (
                  <TableRow key={n.id} className="cursor-pointer" onClick={() => navigate(`/purchases/debit-notes/${n.id}`)}>
                    <TableCell className="font-mono text-sm font-medium">{n.voucherNumber}</TableCell>
                    <TableCell>{n.noteDate}</TableCell>
                    <TableCell>{n.supplier.name}</TableCell>
                    <TableCell className="text-muted-foreground">{n.sourcePurchaseNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{n.noteType.replace('_', ' ')}</TableCell>
                    <TableCell>{money(n.totalAmount)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(n.status)}>{n.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && notes.length === 0 && (
            <EmptyState icon={FilePlus2} title="No debit notes found" description="Create a debit note against a posted purchase." />
          )}

          {!loading && notes.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
