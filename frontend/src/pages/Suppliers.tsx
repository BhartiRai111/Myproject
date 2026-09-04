import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, MoreHorizontal, Pencil, Plus, Power, PowerOff, Search, Truck } from 'lucide-react';
import { supplierApi } from '../api/supplierApi';
import SupplierViewModal from '../components/SupplierViewModal';
import { parseApiError } from '../utils/apiError';
import { Supplier, SupplierStatus } from '../types/supplier';
import { useAuth } from '../context/AuthContext';
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

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'name-asc', label: 'Name (A-Z)' },
  { value: 'name-desc', label: 'Name (Z-A)' },
  { value: 'city-asc', label: 'City (A-Z)' },
  { value: 'createdAt-desc', label: 'Newest First' },
];

export default function Suppliers() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<SupplierStatus | ''>('');
  const [sort, setSort] = useState('name-asc');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewSupplier, setViewSupplier] = useState<Supplier | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<Supplier | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [activatingId, setActivatingId] = useState<number | null>(null);

  const [sortBy, sortDir] = sort.split('-') as [string, 'asc' | 'desc'];

  const loadSuppliers = async () => {
    setLoading(true);
    try {
      const res = await supplierApi.list({
        search,
        status: statusFilter || undefined,
        page,
        size: PAGE_SIZE,
        sortBy,
        sortDir,
      });
      setSuppliers(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load suppliers').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSuppliers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter, sortBy, sortDir]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadSuppliers();
  };

  const handleActivate = async (supplier: Supplier) => {
    setActivatingId(supplier.id);
    try {
      await supplierApi.activate(supplier.id);
      toast.success(`${supplier.name} activated successfully`);
      loadSuppliers();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to activate supplier').message);
    } finally {
      setActivatingId(null);
    }
  };

  const handleDeactivateConfirm = async () => {
    if (!deactivateTarget) return;
    setDeactivating(true);
    try {
      await supplierApi.deactivate(deactivateTarget.id);
      toast.success(`${deactivateTarget.name} deactivated successfully`);
      setDeactivateTarget(null);
      loadSuppliers();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to deactivate supplier').message);
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Suppliers"
        description="Manage the suppliers you purchase stock from."
        actions={
          canManage ? (
            <Button onClick={() => navigate('/suppliers/new')}>
              <Plus className="h-4 w-4" /> Add Supplier
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by name, mobile, or email"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-2 lg:flex lg:shrink-0">
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as SupplierStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="INACTIVE">Inactive</SelectItem>
              </SelectContent>
            </Select>
            <Select value={sort} onValueChange={setSort}>
              <SelectTrigger className="w-full lg:w-44">
                <SelectValue placeholder="Sort" />
              </SelectTrigger>
              <SelectContent>
                {SORT_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Supplier</TableHead>
                <TableHead>Contact</TableHead>
                <TableHead>Mobile</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>City</TableHead>
                <TableHead>GST</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {suppliers.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell className="font-medium">{s.name}</TableCell>
                    <TableCell className="text-muted-foreground">{s.contactPerson || '—'}</TableCell>
                    <TableCell>{s.mobile}</TableCell>
                    <TableCell className="text-muted-foreground">{s.email || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{s.city || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{s.gstNumber || '—'}</TableCell>
                    <TableCell>
                      <Badge variant={s.status === 'ACTIVE' ? 'success' : 'muted'}>{s.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setViewSupplier(s)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {canManage && (
                            <DropdownMenuItem onClick={() => navigate(`/suppliers/${s.id}/edit`)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          )}
                          {canManage && s.status === 'ACTIVE' && (
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={activatingId === s.id}
                              onClick={() => setDeactivateTarget(s)}
                            >
                              <PowerOff className="h-4 w-4" /> Deactivate
                            </DropdownMenuItem>
                          )}
                          {canManage && s.status === 'INACTIVE' && (
                            <DropdownMenuItem disabled={activatingId === s.id} onClick={() => handleActivate(s)}>
                              <Power className="h-4 w-4" /> Activate
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

          {!loading && suppliers.length === 0 && (
            <EmptyState
              icon={Truck}
              title="No suppliers found"
              description="Try adjusting your search or filters, or add a new supplier."
              action={
                canManage ? (
                  <Button size="sm" onClick={() => navigate('/suppliers/new')}>
                    <Plus className="h-4 w-4" /> Add Supplier
                  </Button>
                ) : undefined
              }
            />
          )}

          {!loading && suppliers.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <SupplierViewModal show={!!viewSupplier} supplier={viewSupplier} onClose={() => setViewSupplier(null)} />

      <Dialog open={!!deactivateTarget} onOpenChange={(open) => !open && setDeactivateTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Deactivate Supplier</DialogTitle>
            <DialogDescription>
              Are you sure you want to deactivate <strong>{deactivateTarget?.name}</strong>? It will no longer be
              selectable for new purchases, but existing purchase history will keep showing it.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeactivateTarget(null)} disabled={deactivating}>
              Keep Active
            </Button>
            <Button variant="destructive" loading={deactivating} onClick={handleDeactivateConfirm}>
              Deactivate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
