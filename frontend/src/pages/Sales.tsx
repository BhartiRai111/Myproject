import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, MoreHorizontal, Pencil, Plus, Receipt, Search, XCircle } from 'lucide-react';
import { saleApi } from '../api/saleApi';
import SaleViewModal from '../components/SaleViewModal';
import { useAuth } from '../context/AuthContext';
import { parseApiError } from '../utils/apiError';
import { PaymentStatus } from '../types/purchase';
import { Sale, SaleStatus } from '../types/sale';
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const PAGE_SIZE = 10;
const ALL = '__all__';

function paymentStatusVariant(status: PaymentStatus) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'destructive' as const;
}

function saleStatusVariant(status: SaleStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function Sales() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [paymentStatusFilter, setPaymentStatusFilter] = useState<PaymentStatus | ''>('');
  const [statusFilter, setStatusFilter] = useState<SaleStatus | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewSale, setViewSale] = useState<Sale | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Sale | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const loadSales = async () => {
    setLoading(true);
    try {
      const res = await saleApi.list({
        search,
        paymentStatus: paymentStatusFilter,
        status: statusFilter,
        fromDate,
        toDate,
        page,
        size: PAGE_SIZE,
      });
      setSales(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load sales').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSales();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, paymentStatusFilter, statusFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadSales();
  };

  const handleCancelConfirm = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    try {
      await saleApi.cancel(cancelTarget.id);
      toast.success('Sale cancelled successfully');
      setCancelTarget(null);
      loadSales();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel sale').message);
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Sales"
        description="Record and track customer sales."
        actions={
          <Button onClick={() => navigate('/sales/new')}>
            <Plus className="h-4 w-4" />
            Add Sale
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by invoice number or customer"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:flex lg:shrink-0">
            <Select
              value={paymentStatusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setPaymentStatusFilter(v === ALL ? '' : (v as PaymentStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Payment" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Payments</SelectItem>
                <SelectItem value="PAID">Paid</SelectItem>
                <SelectItem value="PARTIAL">Partial</SelectItem>
                <SelectItem value="UNPAID">Unpaid</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as SaleStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="PENDING">Pending</SelectItem>
                <SelectItem value="COMPLETED">Completed</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
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
                <TableHead>Invoice #</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Date</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead>Payment</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {sales.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell className="font-medium">{s.invoiceNumber}</TableCell>
                    <TableCell>
                      {s.customer ? `${s.customer.firstName} ${s.customer.lastName || ''}` : 'Walk-in'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{s.saleDate}</TableCell>
                    <TableCell className="text-right font-medium">{s.totalAmount.toFixed(2)}</TableCell>
                    <TableCell>
                      <Badge variant={paymentStatusVariant(s.paymentStatus)}>{s.paymentStatus}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={saleStatusVariant(s.status)}>{s.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setViewSale(s)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {canManage && s.status !== 'CANCELLED' && (
                            <DropdownMenuItem onClick={() => navigate(`/sales/${s.id}/edit`)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          )}
                          {canManage && s.status !== 'CANCELLED' && (
                            <DropdownMenuItem variant="destructive" onClick={() => setCancelTarget(s)}>
                              <XCircle className="h-4 w-4" /> Cancel
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && sales.length === 0 && (
            <EmptyState
              icon={Receipt}
              title="No sales found"
              description="Try adjusting your search or filters, or record a new sale."
              action={
                <Button size="sm" onClick={() => navigate('/sales/new')}>
                  <Plus className="h-4 w-4" /> Add Sale
                </Button>
              }
            />
          )}

          {!loading && sales.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <SaleViewModal show={!!viewSale} sale={viewSale} onClose={() => setViewSale(null)} />

      <Dialog open={!!cancelTarget} onOpenChange={(open) => !open && setCancelTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Cancel Sale</DialogTitle>
            <DialogDescription>
              Are you sure you want to cancel sale <strong>{cancelTarget?.invoiceNumber}</strong>? This cannot be
              undone. If this sale was completed, its sold quantities will be restored to stock, and it will remain
              in sales history with a Cancelled status.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={cancelling}>
              Keep Sale
            </Button>
            <Button variant="destructive" loading={cancelling} onClick={handleCancelConfirm}>
              Cancel Sale
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
