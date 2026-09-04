import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ClipboardList, Eye, MoreHorizontal, Pencil, Plus, Search, FilePlus, XCircle } from 'lucide-react';
import { salesOrderApi } from '../../api/salesOrderApi';
import { customerApi } from '../../api/customerApi';
import { parseApiError } from '../../utils/apiError';
import { Customer } from '../../types/sale';
import { SalesOrder, SalesOrderStatus } from '../../types/salesOrder';
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

function statusVariant(status: SalesOrderStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  if (status === 'PARTIALLY_BILLED') return 'warning' as const;
  return 'muted' as const;
}

export default function SalesOrders() {
  const navigate = useNavigate();

  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [customerFilter, setCustomerFilter] = useState<number | ''>('');
  const [statusFilter, setStatusFilter] = useState<SalesOrderStatus | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [cancelTarget, setCancelTarget] = useState<SalesOrder | null>(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    customerApi.list().then((res) => setCustomers(res.data));
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await salesOrderApi.list({
        search,
        customerId: customerFilter,
        status: statusFilter,
        fromDate,
        toDate,
        page,
        size: PAGE_SIZE,
      });
      setOrders(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load sales orders').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, customerFilter, statusFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const handleCancelConfirm = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    try {
      await salesOrderApi.cancel(cancelTarget.id);
      toast.success('Sales order cancelled');
      setCancelTarget(null);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel sales order').message);
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Sales" onClick={() => navigate('/sales')} />

      <PageHeader
        title="Sales Orders"
        description="Orders placed by customers before they are billed."
        actions={
          <Button onClick={() => navigate('/sales/orders/new')}>
            <Plus className="h-4 w-4" />
            New Order
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by order number or customer"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:flex lg:shrink-0">
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
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as SalesOrderStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-40">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="CONFIRMED">Confirmed</SelectItem>
                <SelectItem value="PARTIALLY_BILLED">Partially Billed</SelectItem>
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
                <TableHead>Order No.</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead className="text-right">Billed</TableHead>
                <TableHead className="text-right">Remaining</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {orders.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-medium">{o.orderNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{o.orderDate}</TableCell>
                    <TableCell>
                      {o.customer ? `${o.customer.firstName} ${o.customer.lastName || ''}` : '-'}
                    </TableCell>
                    <TableCell className="text-right font-medium">{o.totalAmount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{o.billedAmount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{o.remainingAmount.toFixed(2)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(o.status)}>{o.status.replace('_', ' ')}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => navigate(`/sales/orders/${o.id}`)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {(o.status === 'DRAFT' || o.status === 'CONFIRMED' || o.status === 'PARTIALLY_BILLED') && (
                            <DropdownMenuItem onClick={() => navigate(`/sales/orders/${o.id}/edit`)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          )}
                          {o.status !== 'COMPLETED' && o.status !== 'CANCELLED' && (
                            <DropdownMenuItem onClick={() => navigate(`/sales/bills/new?orderId=${o.id}`)}>
                              <FilePlus className="h-4 w-4" /> Create Bill
                            </DropdownMenuItem>
                          )}
                          {(o.status === 'DRAFT' || o.status === 'CONFIRMED') && (
                            <DropdownMenuItem variant="destructive" onClick={() => setCancelTarget(o)}>
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

          {!loading && orders.length === 0 && (
            <EmptyState
              icon={ClipboardList}
              title="No sales orders found"
              description="Try adjusting your search or filters, or create a new order."
              action={
                <Button size="sm" onClick={() => navigate('/sales/orders/new')}>
                  <Plus className="h-4 w-4" /> New Order
                </Button>
              }
            />
          )}

          {!loading && orders.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!cancelTarget} onOpenChange={(open) => !open && setCancelTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Cancel Sales Order</DialogTitle>
            <DialogDescription>
              Are you sure you want to cancel order <strong>{cancelTarget?.orderNumber}</strong>? This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={cancelling}>
              Keep Order
            </Button>
            <Button variant="destructive" loading={cancelling} onClick={handleCancelConfirm}>
              Cancel Order
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
