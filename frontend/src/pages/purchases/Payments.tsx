import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, IndianRupee, MoreHorizontal, Plus, Printer, Search, Trash2 } from 'lucide-react';
import { paymentApi } from '../../api/paymentApi';
import { supplierApi } from '../../api/supplierApi';
import { parseApiError } from '../../utils/apiError';
import { Supplier } from '../../types/purchase';
import { Payment } from '../../types/payment';
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

export default function Payments() {
  const navigate = useNavigate();

  const [payments, setPayments] = useState<Payment[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [supplierFilter, setSupplierFilter] = useState<number | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<Payment | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    supplierApi.list({ size: 200 }).then((res) => setSuppliers(res.data.content));
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await paymentApi.list({ search, supplierId: supplierFilter, fromDate, toDate, page, size: PAGE_SIZE });
      setPayments(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load payments').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, supplierFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await paymentApi.remove(deleteTarget.id);
      toast.success('Payment deleted and reversed');
      setDeleteTarget(null);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete payment').message);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Purchases" onClick={() => navigate('/purchases')} />

      <PageHeader
        title="Payment Entries"
        description="Payments made to suppliers against outstanding purchase bills."
        actions={
          <Button onClick={() => navigate('/purchases/payments/new')}>
            <Plus className="h-4 w-4" />
            New Payment
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by payment number or supplier"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:flex lg:shrink-0">
            <Select
              value={supplierFilter ? String(supplierFilter) : ALL}
              onValueChange={(v) => {
                setPage(0);
                setSupplierFilter(v === ALL ? '' : Number(v));
              }}
            >
              <SelectTrigger className="w-full lg:w-40">
                <SelectValue placeholder="Supplier" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Suppliers</SelectItem>
                {suppliers.map((s) => (
                  <SelectItem key={s.id} value={String(s.id)}>
                    {s.name}
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
                <TableHead>Payment No.</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Supplier</TableHead>
                <TableHead>Reference Bill(s)</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Payment Mode</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {payments.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{p.paymentNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{p.paymentDate}</TableCell>
                    <TableCell>{p.supplier ? p.supplier.name : '-'}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {p.allocations.length > 0 ? p.allocations.map((a) => a.purchaseNumber).join(', ') : 'On account'}
                    </TableCell>
                    <TableCell className="text-right font-medium">{p.amount.toFixed(2)}</TableCell>
                    <TableCell>{p.paymentMode}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => navigate(`/purchases/payments/${p.id}`)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => navigate(`/purchases/payments/${p.id}`)}>
                            <Printer className="h-4 w-4" /> Print
                          </DropdownMenuItem>
                          <DropdownMenuItem variant="destructive" onClick={() => setDeleteTarget(p)}>
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

          {!loading && payments.length === 0 && (
            <EmptyState
              icon={IndianRupee}
              title="No payments found"
              description="Try adjusting your search or filters, or record a new payment."
              action={
                <Button size="sm" onClick={() => navigate('/purchases/payments/new')}>
                  <Plus className="h-4 w-4" /> New Payment
                </Button>
              }
            />
          )}

          {!loading && payments.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Payment"
        description={`Deleting payment ${deleteTarget?.paymentNumber} will restore the payable amount on its linked bill(s) and reverse the cash/bank entry. This cannot be undone.`}
        confirmLabel={deleting ? 'Deleting...' : 'Delete Payment'}
        cancelLabel="Keep Payment"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
