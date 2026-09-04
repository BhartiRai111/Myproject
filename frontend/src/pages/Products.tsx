import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, FolderCog, MoreHorizontal, Package, Pencil, Plus, Power, PowerOff, Search } from 'lucide-react';
import { productApi } from '../api/productApi';
import { categoryApi } from '../api/categoryApi';
import ProductViewModal from '../components/ProductViewModal';
import CategoryManagerDialog from '../components/CategoryManagerDialog';
import { parseApiError } from '../utils/apiError';
import { getStockStatus, stockStatusLabel, stockStatusVariant, StockStatus } from '../utils/stockStatus';
import { Product, ProductStatus, Category } from '../types/product';
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
  { value: 'sellingPrice-asc', label: 'Selling Price (Low-High)' },
  { value: 'sellingPrice-desc', label: 'Selling Price (High-Low)' },
  { value: 'createdAt-desc', label: 'Newest First' },
];

export default function Products() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<ProductStatus | ''>('');
  const [stockFilter, setStockFilter] = useState<StockStatus | ''>('');
  const [sort, setSort] = useState('name-asc');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewProduct, setViewProduct] = useState<Product | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<Product | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [activatingId, setActivatingId] = useState<number | null>(null);
  const [showCategoryManager, setShowCategoryManager] = useState(false);

  const [sortBy, sortDir] = sort.split('-') as [string, 'asc' | 'desc'];

  const loadCategories = async () => {
    try {
      const res = await categoryApi.list();
      setCategories(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load categories').message);
    }
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      const res = await productApi.list({
        search,
        categoryId: categoryFilter ? Number(categoryFilter) : undefined,
        status: statusFilter || undefined,
        page,
        size: PAGE_SIZE,
        sortBy,
        sortDir,
      });
      setProducts(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load products').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, categoryFilter, statusFilter, sortBy, sortDir]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadProducts();
  };

  const handleActivate = async (product: Product) => {
    setActivatingId(product.id);
    try {
      await productApi.activate(product.id);
      toast.success(`${product.name} activated successfully`);
      loadProducts();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to activate product').message);
    } finally {
      setActivatingId(null);
    }
  };

  const handleDeactivateConfirm = async () => {
    if (!deactivateTarget) return;
    setDeactivating(true);
    try {
      await productApi.deactivate(deactivateTarget.id);
      toast.success(`${deactivateTarget.name} deactivated successfully`);
      setDeactivateTarget(null);
      loadProducts();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to deactivate product').message);
    } finally {
      setDeactivating(false);
    }
  };

  const visibleProducts = stockFilter
    ? products.filter((p) => getStockStatus(p) === stockFilter)
    : products;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Products"
        description="Manage your product catalog, pricing, and stock levels."
        actions={
          <div className="flex gap-2">
            {canManage && (
              <Button variant="outline" onClick={() => setShowCategoryManager(true)}>
                <FolderCog className="h-4 w-4" /> Categories
              </Button>
            )}
            {canManage && (
              <Button onClick={() => navigate('/products/new')}>
                <Plus className="h-4 w-4" /> Add Product
              </Button>
            )}
          </div>
        }
      />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by name, SKU, or barcode"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:flex lg:shrink-0">
            <Select
              value={categoryFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setCategoryFilter(v === ALL ? '' : v);
              }}
            >
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Category" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Categories</SelectItem>
                {categories.map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as ProductStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-32">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="INACTIVE">Inactive</SelectItem>
              </SelectContent>
            </Select>
            <Select value={stockFilter || ALL} onValueChange={(v) => setStockFilter(v === ALL ? '' : (v as StockStatus))}>
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Stock" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Stock</SelectItem>
                <SelectItem value="IN_STOCK">In Stock</SelectItem>
                <SelectItem value="LOW_STOCK">Low Stock</SelectItem>
                <SelectItem value="OUT_OF_STOCK">Out of Stock</SelectItem>
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
                <TableHead>Product</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Brand</TableHead>
                <TableHead className="text-right">Purchase Price</TableHead>
                <TableHead className="text-right">Selling Price</TableHead>
                <TableHead className="text-right">Stock</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={9} />
            ) : (
              <TableBody>
                {visibleProducts.map((p) => {
                  const stockStatus = getStockStatus(p);
                  return (
                    <TableRow key={p.id}>
                      <TableCell className="font-medium">{p.name}</TableCell>
                      <TableCell className="text-muted-foreground">{p.sku}</TableCell>
                      <TableCell>{p.categoryName}</TableCell>
                      <TableCell className="text-muted-foreground">{p.brand || '—'}</TableCell>
                      <TableCell className="text-right">{p.purchasePrice.toFixed(2)}</TableCell>
                      <TableCell className="text-right font-medium">{p.sellingPrice.toFixed(2)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-col items-end gap-1">
                          <span>{p.stockQuantity}</span>
                          <Badge variant={stockStatusVariant(stockStatus)} className="text-[10px]">
                            {stockStatusLabel(stockStatus)}
                          </Badge>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={p.status === 'ACTIVE' ? 'success' : 'muted'}>{p.status}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => setViewProduct(p)}>
                              <Eye className="h-4 w-4" /> View
                            </DropdownMenuItem>
                            {canManage && (
                              <DropdownMenuItem onClick={() => navigate(`/products/${p.id}/edit`)}>
                                <Pencil className="h-4 w-4" /> Edit
                              </DropdownMenuItem>
                            )}
                            {canManage && p.status === 'ACTIVE' && (
                              <DropdownMenuItem
                                variant="destructive"
                                disabled={activatingId === p.id}
                                onClick={() => setDeactivateTarget(p)}
                              >
                                <PowerOff className="h-4 w-4" /> Deactivate
                              </DropdownMenuItem>
                            )}
                            {canManage && p.status === 'INACTIVE' && (
                              <DropdownMenuItem disabled={activatingId === p.id} onClick={() => handleActivate(p)}>
                                <Power className="h-4 w-4" /> Activate
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            )}
          </Table>

          {!loading && visibleProducts.length === 0 && (
            <EmptyState
              icon={Package}
              title="No products found"
              description="Try adjusting your search or filters, or add a new product."
              action={
                canManage ? (
                  <Button size="sm" onClick={() => navigate('/products/new')}>
                    <Plus className="h-4 w-4" /> Add Product
                  </Button>
                ) : undefined
              }
            />
          )}

          {!loading && visibleProducts.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <ProductViewModal show={!!viewProduct} product={viewProduct} onClose={() => setViewProduct(null)} />

      <CategoryManagerDialog
        show={showCategoryManager}
        onClose={() => setShowCategoryManager(false)}
        onChanged={() => {
          loadCategories();
          loadProducts();
        }}
      />

      <Dialog open={!!deactivateTarget} onOpenChange={(open) => !open && setDeactivateTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Deactivate Product</DialogTitle>
            <DialogDescription>
              Are you sure you want to deactivate <strong>{deactivateTarget?.name}</strong>? It will no longer be
              selectable for new purchases or sales, but existing records will keep showing it.
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
