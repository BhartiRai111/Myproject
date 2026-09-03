import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, MoreHorizontal, Pencil, Plus, Search, ShoppingCart, XCircle } from 'lucide-react';
import { purchaseApi } from '../api/purchaseApi';
import PurchaseViewModal from '../components/PurchaseViewModal';
import { parseApiError } from '../utils/apiError';
import { PaymentStatus, Purchase, PurchaseStatus } from '../types/purchase';
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

function purchaseStatusVariant(status: PurchaseStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function Purchases() {
  const navigate = useNavigate();

  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [paymentStatusFilter, setPaymentStatusFilter] = useState<PaymentStatus | ''>('');
  const [statusFilter, setStatusFilter] = useState<PurchaseStatus | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewPurchase, setViewPurchase] = useState<Purchase | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Purchase | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const loadPurchases = async () => {
    setLoading(true);
    try {
      const res = await purchaseApi.list({
        search,
        paymentStatus: paymentStatusFilter,
        status: statusFilter,
        fromDate,
        toDate,
        page,
        size: PAGE_SIZE,
      });
      setPurchases(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load purchases').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPurchases();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, paymentStatusFilter, statusFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadPurchases();
  };

  const handleCancelConfirm = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    try {
      await purchaseApi.cancel(cancelTarget.id);
      toast.success('Purchase cancelled successfully');
      setCancelTarget(null);
      loadPurchases();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel purchase').message);
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Purchases"
        description="Track stock ordered from your suppliers."
        actions={
          <Button onClick={() => navigate('/purchases/new')}>
            <Plus className="h-4 w-4" />
            Add Purchase
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by purchase number or supplier"
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
                setStatusFilter(v === ALL ? '' : (v as PurchaseStatus));
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
                <TableHead>Purchase #</TableHead>
                <TableHead>Supplier</TableHead>
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
                {purchases.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{p.purchaseNumber}</TableCell>
                    <TableCell>{p.supplier.name}</TableCell>
                    <TableCell className="text-muted-foreground">{p.purchaseDate}</TableCell>
                    <TableCell className="text-right font-medium">{p.totalAmount.toFixed(2)}</TableCell>
                    <TableCell>
                      <Badge variant={paymentStatusVariant(p.paymentStatus)}>{p.paymentStatus}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={purchaseStatusVariant(p.status)}>{p.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setViewPurchase(p)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {p.status !== 'CANCELLED' && (
                            <DropdownMenuItem onClick={() => navigate(`/purchases/${p.id}/edit`)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          )}
                          {p.status !== 'CANCELLED' && (
                            <DropdownMenuItem variant="destructive" onClick={() => setCancelTarget(p)}>
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

          {!loading && purchases.length === 0 && (
            <EmptyState
              icon={ShoppingCart}
              title="No purchases found"
              description="Try adjusting your search or filters, or create a new purchase."
              action={
                <Button size="sm" onClick={() => navigate('/purchases/new')}>
                  <Plus className="h-4 w-4" /> Add Purchase
                </Button>
              }
            />
          )}

          {!loading && purchases.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <PurchaseViewModal show={!!viewPurchase} purchase={viewPurchase} onClose={() => setViewPurchase(null)} />

      <Dialog open={!!cancelTarget} onOpenChange={(open) => !open && setCancelTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Cancel Purchase</DialogTitle>
            <DialogDescription>
              Are you sure you want to cancel purchase <strong>{cancelTarget?.purchaseNumber}</strong>? This cannot
              be undone, and the purchase will remain in history with a Cancelled status.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={cancelling}>
              Keep Purchase
            </Button>
            <Button variant="destructive" loading={cancelling} onClick={handleCancelConfirm}>
              Cancel Purchase
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
