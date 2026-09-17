import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeftRight, Eye, MoreHorizontal, Plus, Search } from 'lucide-react';
import { stockTransferApi } from '../../api/stockTransferApi';
import { parseApiError } from '../../utils/apiError';
import { StockTransfer, StockTransferStatus } from '../../types/stockTransfer';
import { useAuth } from '../../context/AuthContext';
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

const PAGE_SIZE = 20;
const ALL = '__all__';

function statusVariant(status: StockTransferStatus) {
  if (status === 'RECEIVED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  if (status === 'DISPATCHED') return 'warning' as const;
  if (status === 'APPROVED') return 'secondary' as const;
  return 'muted' as const;
}

export default function StockTransfers() {
  const navigate = useNavigate();
  const { hasPermission, myStores } = useAuth();

  const [transfers, setTransfers] = useState<StockTransfer[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StockTransferStatus | ''>('');
  const [storeFilter, setStoreFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = async () => {
    setLoading(true);
    try {
      const res = await stockTransferApi.list({
        search,
        status: statusFilter,
        storeId: storeFilter ? Number(storeFilter) : undefined,
        page,
        size: PAGE_SIZE,
      });
      setTransfers(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load stock transfers').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter, storeFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const runAction = async (action: () => Promise<unknown>, successMessage: string) => {
    try {
      await action();
      toast.success(successMessage);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Action failed').message);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Inventory" onClick={() => navigate('/inventory')} />

      <PageHeader
        title="Stock Transfers"
        description="Move stock between stores, branches and warehouses."
        actions={
          hasPermission('STOCK_TRANSFER_CREATE') && (
            <Button onClick={() => navigate('/inventory/stock-transfers/new')}>
              <Plus className="h-4 w-4" />
              New Transfer
            </Button>
          )
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by transfer number"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:flex lg:shrink-0">
            {myStores.length > 1 && (
              <Select
                value={storeFilter || ALL}
                onValueChange={(v) => {
                  setPage(0);
                  setStoreFilter(v === ALL ? '' : v);
                }}
              >
                <SelectTrigger className="w-full lg:w-40">
                  <SelectValue placeholder="Store" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All Stores</SelectItem>
                  {myStores.map((s) => (
                    <SelectItem key={s.id} value={String(s.id)}>
                      {s.storeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as StockTransferStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-40">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="APPROVED">Approved</SelectItem>
                <SelectItem value="DISPATCHED">Dispatched</SelectItem>
                <SelectItem value="RECEIVED">Received</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
              </SelectContent>
            </Select>
            <Button type="submit" variant="secondary" onClick={handleSearchSubmit}>
              Search
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Transfer No.</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>From</TableHead>
                <TableHead>To</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={6} />
            ) : (
              <TableBody>
                {transfers.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell className="font-medium">{t.transferNumber}</TableCell>
                    <TableCell className="text-muted-foreground">{t.transferDate}</TableCell>
                    <TableCell>{t.fromStoreName}</TableCell>
                    <TableCell>{t.toStoreName}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => navigate(`/inventory/stock-transfers/${t.id}`)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {t.status === 'DRAFT' && hasPermission('STOCK_TRANSFER_APPROVE') && (
                            <DropdownMenuItem
                              onClick={() => runAction(() => stockTransferApi.approve(t.id), 'Transfer approved')}
                            >
                              Approve
                            </DropdownMenuItem>
                          )}
                          {t.status === 'APPROVED' && hasPermission('STOCK_TRANSFER_DISPATCH') && (
                            <DropdownMenuItem
                              onClick={() => runAction(() => stockTransferApi.dispatch(t.id), 'Transfer dispatched')}
                            >
                              Dispatch
                            </DropdownMenuItem>
                          )}
                          {t.status === 'DISPATCHED' && hasPermission('STOCK_TRANSFER_RECEIVE') && (
                            <DropdownMenuItem
                              onClick={() => runAction(() => stockTransferApi.receive(t.id), 'Transfer received')}
                            >
                              Receive
                            </DropdownMenuItem>
                          )}
                          {(t.status === 'DRAFT' || t.status === 'APPROVED') && hasPermission('STOCK_TRANSFER_CANCEL') && (
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() => runAction(() => stockTransferApi.cancel(t.id), 'Transfer cancelled')}
                            >
                              Cancel
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

          {!loading && transfers.length === 0 && (
            <EmptyState
              icon={ArrowLeftRight}
              title="No stock transfers found"
              description="Try adjusting your search or filters, or create a new transfer."
            />
          )}

          {!loading && transfers.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
