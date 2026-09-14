import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, FileText, MoreHorizontal, Pencil, Plus, Printer, Search, Send, Trash2 } from 'lucide-react';
import { saleApi } from '../../api/saleApi';
import { parseApiError } from '../../utils/apiError';
import { Sale, SaleStatus } from '../../types/sale';
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

function statusVariant(status: SaleStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'DRAFT') return 'warning' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

const money = (n: number) => (n ?? 0).toFixed(2);

export default function KacchiSales() {
  const navigate = useNavigate();

  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<SaleStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<Sale | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [postingId, setPostingId] = useState<number | null>(null);

  const loadSales = async () => {
    setLoading(true);
    try {
      const res = await saleApi.list({
        search,
        status: statusFilter,
        transactionType: 'SALE_CHALLAN',
        page,
        size: PAGE_SIZE,
      });
      setSales(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Kacchi sales').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSales();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadSales();
  };

  const handlePost = async (sale: Sale) => {
    setPostingId(sale.id);
    try {
      await saleApi.post(sale.id);
      toast.success(`${sale.invoiceNumber} posted successfully`);
      loadSales();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to post challan').message);
    } finally {
      setPostingId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await saleApi.remove(deleteTarget.id);
      toast.success('Kacchi Sale deleted');
      setDeleteTarget(null);
      loadSales();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete Kacchi Sale').message);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Sales" onClick={() => navigate('/sales')} />

      <PageHeader
        title="Kacchi Sales / Sale Challan"
        description="GST is still calculated in full on every challan below — it is simply excluded from GST return reporting."
        actions={
          <Button onClick={() => navigate('/sales/kacchi/new')}>
            <Plus className="h-4 w-4" />
            New Kacchi Sale
          </Button>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by challan number or customer"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <Select
            value={statusFilter || ALL}
            onValueChange={(v) => {
              setPage(0);
              setStatusFilter(v === ALL ? '' : (v as SaleStatus));
            }}
          >
            <SelectTrigger className="w-full lg:w-40">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All Status</SelectItem>
              <SelectItem value="DRAFT">Draft</SelectItem>
              <SelectItem value="COMPLETED">Posted</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Challan #</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>GSTIN</TableHead>
                  <TableHead className="text-right">Taxable</TableHead>
                  <TableHead className="text-right">CGST</TableHead>
                  <TableHead className="text-right">SGST</TableHead>
                  <TableHead className="text-right">IGST</TableHead>
                  <TableHead className="text-right">Grand Total</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>GST Reporting</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              {loading ? (
                <TableSkeleton columns={12} />
              ) : (
                <TableBody>
                  {sales.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-medium">{s.invoiceNumber}</TableCell>
                      <TableCell className="text-muted-foreground">{s.saleDate}</TableCell>
                      <TableCell>{s.customer ? `${s.customer.firstName} ${s.customer.lastName || ''}` : 'Walk-in'}</TableCell>
                      <TableCell className="text-muted-foreground">{s.customerGstin || '—'}</TableCell>
                      <TableCell className="text-right">{money(s.taxableAmount)}</TableCell>
                      <TableCell className="text-right">{money(s.cgstAmount)}</TableCell>
                      <TableCell className="text-right">{money(s.sgstAmount)}</TableCell>
                      <TableCell className="text-right">{money(s.igstAmount)}</TableCell>
                      <TableCell className="text-right font-medium">{money(s.totalAmount)}</TableCell>
                      <TableCell>
                        <Badge variant={statusVariant(s.status)}>{s.status}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={s.gstReportingApplicable ? 'success' : 'muted'}>
                          {s.gstReportingApplicable ? 'YES' : 'NO'}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => navigate(`/sales/kacchi/${s.id}`)}>
                              <Eye className="h-4 w-4" /> View
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => navigate(`/sales/kacchi/${s.id}`)}>
                              <Printer className="h-4 w-4" /> Print
                            </DropdownMenuItem>
                            {s.status === 'DRAFT' && (
                              <DropdownMenuItem onClick={() => navigate(`/sales/kacchi/${s.id}/edit`)}>
                                <Pencil className="h-4 w-4" /> Edit
                              </DropdownMenuItem>
                            )}
                            {s.status === 'DRAFT' && (
                              <DropdownMenuItem disabled={postingId === s.id} onClick={() => handlePost(s)}>
                                <Send className="h-4 w-4" /> Post
                              </DropdownMenuItem>
                            )}
                            {s.status !== 'CANCELLED' && (
                              <DropdownMenuItem variant="destructive" onClick={() => setDeleteTarget(s)}>
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
          </div>

          {!loading && sales.length === 0 && (
            <EmptyState
              icon={FileText}
              title="No Kacchi sales found"
              description="Try adjusting your search or filters, or create a new Kacchi Sale / Sale Challan."
              action={
                <Button size="sm" onClick={() => navigate('/sales/kacchi/new')}>
                  <Plus className="h-4 w-4" /> New Kacchi Sale
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

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Kacchi Sale"
        description={
          deleteTarget?.status === 'DRAFT'
            ? `Deleting draft challan ${deleteTarget?.invoiceNumber} will remove it. It was never posted, so nothing needs to be reversed.`
            : `Deleting challan ${deleteTarget?.invoiceNumber} will restore its stock, reverse the customer account entry, and reverse the accounting journal. This cannot be undone.`
        }
        confirmLabel={deleting ? 'Deleting...' : 'Delete'}
        cancelLabel="Keep"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
