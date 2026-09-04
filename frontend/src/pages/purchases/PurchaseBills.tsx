import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, FileText, IndianRupee, MoreHorizontal, Pencil, Plus, Printer, Search, Trash2 } from 'lucide-react';
import { purchaseApi } from '../../api/purchaseApi';
import { parseApiError } from '../../utils/apiError';
import { PaymentStatus, Purchase, PurchaseStatus } from '../../types/purchase';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
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

const PAGE_SIZE = 10;
const ALL = '__all__';

function paymentStatusVariant(status: PaymentStatus) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'destructive' as const;
}

export default function PurchaseBills() {
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
  const [deleteTarget, setDeleteTarget] = useState<Purchase | null>(null);
  const [deleting, setDeleting] = useState(false);

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
      toast.error(parseApiError(err, 'Failed to load purchase bills').message);
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

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await purchaseApi.remove(deleteTarget.id);
      toast.success('Purchase bill deleted and fully reversed');
      setDeleteTarget(null);
      loadPurchases();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete purchase bill').message);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Purchases" onClick={() => navigate('/purchases')} />

      <PageHeader
        title="Purchase Bills"
        description="GST and Non-GST bills received from suppliers."
        actions={
          <Button onClick={() => navigate('/purchases/bills/new')}>
            <Plus className="h-4 w-4" />
            New Bill
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by bill number or supplier"
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
                <TableHead>Bill #</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Supplier</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead className="text-right">Paid</TableHead>
                <TableHead className="text-right">Payable</TableHead>
                <TableHead>Payment</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={9} />
            ) : (
              <TableBody>
                {purchases.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{p.purchaseNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{p.purchaseDate}</TableCell>
                    <TableCell>{p.supplier ? p.supplier.name : '-'}</TableCell>
                    <TableCell>
                      <Badge variant={p.gstType === 'GST' ? 'secondary' : 'muted'}>
                        {p.gstType === 'GST' ? 'GST' : 'Non-GST'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right font-medium">{p.totalAmount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{p.paidAmount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{p.payableAmount.toFixed(2)}</TableCell>
                    <TableCell>
                      <Badge variant={paymentStatusVariant(p.paymentStatus)}>{p.paymentStatus}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => navigate(`/purchases/bills/${p.id}`)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => navigate(`/purchases/bills/${p.id}`)}>
                            <Printer className="h-4 w-4" /> Print
                          </DropdownMenuItem>
                          {p.status !== 'CANCELLED' && !p.hasPayments && (
                            <DropdownMenuItem onClick={() => navigate(`/purchases/bills/${p.id}/edit`)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          )}
                          {p.payableAmount > 0 && p.supplier && (
                            <DropdownMenuItem onClick={() => navigate(`/purchases/payments/new?supplierId=${p.supplier!.id}`)}>
                              <IndianRupee className="h-4 w-4" /> Payment
                            </DropdownMenuItem>
                          )}
                          {p.status !== 'CANCELLED' && (
                            <DropdownMenuItem variant="destructive" onClick={() => setDeleteTarget(p)}>
                              <Trash2 className="h-4 w-4" /> Delete
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
              icon={FileText}
              title="No purchase bills found"
              description="Try adjusting your search or filters, or create a new bill."
              action={
                <Button size="sm" onClick={() => navigate('/purchases/bills/new')}>
                  <Plus className="h-4 w-4" /> New Bill
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

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Purchase Bill"
        description={`Deleting bill ${deleteTarget?.purchaseNumber} will reverse its stock, the supplier account entry, and any GST entry. This cannot be undone.`}
        confirmLabel={deleting ? 'Deleting...' : 'Delete Bill'}
        cancelLabel="Keep Bill"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
