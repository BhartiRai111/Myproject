import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, IndianRupee, MoreHorizontal, Plus, Printer, Search, Trash2 } from 'lucide-react';
import { receiptApi } from '../../api/receiptApi';
import { customerApi } from '../../api/customerApi';
import { parseApiError } from '../../utils/apiError';
import { Customer } from '../../types/sale';
import { Receipt } from '../../types/receipt';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const PAGE_SIZE = 10;
const ALL = '__all__';

export default function Receipts() {
  const navigate = useNavigate();

  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [customerFilter, setCustomerFilter] = useState<number | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<Receipt | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    customerApi.list().then((res) => setCustomers(res.data));
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await receiptApi.list({ search, customerId: customerFilter, fromDate, toDate, page, size: PAGE_SIZE });
      setReceipts(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load receipts').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, customerFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await receiptApi.remove(deleteTarget.id);
      toast.success('Receipt deleted and reversed');
      setDeleteTarget(null);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete receipt').message);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Sales" onClick={() => navigate('/sales')} />

      <PageHeader
        title="Receipt Entries"
        description="Payments received from customers against outstanding sales bills."
        actions={
          <Button onClick={() => navigate('/sales/receipts/new')}>
            <Plus className="h-4 w-4" />
            New Receipt
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by receipt number or customer"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:flex lg:shrink-0">
            <Select
              value={customerFilter ? String(customerFilter) : ALL}
              onValueChange={(v) => {
                setPage(0);
                setCustomerFilter(v === ALL ? '' : Number(v));
              }}
            >
              <SelectTrigger className="w-full lg:w-40">
                <SelectValue placeholder="Customer" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Customers</SelectItem>
                {customers.map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.firstName} {c.lastName || ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              type="date"
              value={fromDate}
              onChange={(e) => {
                setPage(0);
                setFromDate(e.target.value);
              }}
              className="w-full lg:w-40"
            />
            <Input
              type="date"
              value={toDate}
              onChange={(e) => {
                setPage(0);
                setToDate(e.target.value);
              }}
              className="w-full lg:w-40"
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Receipt No.</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Reference Invoice(s)</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Payment Mode</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {receipts.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.receiptNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{r.receiptDate}</TableCell>
                    <TableCell>{r.customer ? `${r.customer.firstName} ${r.customer.lastName || ''}` : '-'}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {r.allocations.length > 0 ? r.allocations.map((a) => a.invoiceNumber).join(', ') : 'On account'}
                    </TableCell>
                    <TableCell className="text-right font-medium">{r.amount.toFixed(2)}</TableCell>
                    <TableCell>{r.paymentMode}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => navigate(`/sales/receipts/${r.id}`)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => navigate(`/sales/receipts/${r.id}`)}>
                            <Printer className="h-4 w-4" /> Print
                          </DropdownMenuItem>
                          <DropdownMenuItem variant="destructive" onClick={() => setDeleteTarget(r)}>
                            <Trash2 className="h-4 w-4" /> Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && receipts.length === 0 && (
            <EmptyState
              icon={IndianRupee}
              title="No receipts found"
              description="Try adjusting your search or filters, or record a new receipt."
              action={
                <Button size="sm" onClick={() => navigate('/sales/receipts/new')}>
                  <Plus className="h-4 w-4" /> New Receipt
                </Button>
              }
            />
          )}

          {!loading && receipts.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Receipt"
        description={`Deleting receipt ${deleteTarget?.receiptNumber} will restore the due amount on its linked bill(s) and reverse the cash/bank entry. This cannot be undone.`}
        confirmLabel={deleting ? 'Deleting...' : 'Delete Receipt'}
        cancelLabel="Keep Receipt"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
