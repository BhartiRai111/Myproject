import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { AlertTriangle, Boxes, Eye, History, MoreHorizontal, Package, PackageX, Search, SlidersHorizontal } from 'lucide-react';
import { inventoryApi } from '../api/inventoryApi';
import { categoryApi } from '../api/categoryApi';
import InventoryViewModal from '../components/InventoryViewModal';
import StockAdjustmentDialog from '../components/StockAdjustmentDialog';
import StockHistoryModal from '../components/StockHistoryModal';
import { parseApiError } from '../utils/apiError';
import { Inventory, StockStatus } from '../types/inventory';
import { Category } from '../types/product';
import { useAuth } from '../context/AuthContext';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
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

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'updatedAt-desc', label: 'Recently Updated' },
  { value: 'currentStock-asc', label: 'Stock (Low-High)' },
  { value: 'currentStock-desc', label: 'Stock (High-Low)' },
];

function stockStatusVariant(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'destructive' as const;
  if (status === 'LOW_STOCK') return 'warning' as const;
  return 'success' as const;
}

function stockStatusLabel(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'Out of Stock';
  if (status === 'LOW_STOCK') return 'Low Stock';
  return 'In Stock';
}

interface SummaryCardData {
  key: string;
  label: string;
  value: string;
  icon: typeof Package;
}

function SummaryCard({ data }: { data: SummaryCardData }) {
  const Icon = data.icon;
  return (
    <Card className="overflow-hidden border-border/80 bg-gradient-to-br from-card to-muted/30">
      <CardContent className="flex items-center justify-between gap-3 p-5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{data.label}</p>
          <p className="mt-1 text-2xl font-bold tracking-tight">{data.value}</p>
        </div>
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  );
}

function SummaryCardSkeleton() {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-3 p-5">
        <div className="w-full space-y-2">
          <Skeleton className="h-3.5 w-24" />
          <Skeleton className="h-7 w-16" />
        </div>
        <Skeleton className="h-10 w-10 rounded-lg" />
      </CardContent>
    </Card>
  );
}

export default function InventoryPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [items, setItems] = useState<Inventory[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summary, setSummary] = useState({ totalProducts: 0, totalStockUnits: 0, lowStockCount: 0, outOfStockCount: 0 });

  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [stockStatusFilter, setStockStatusFilter] = useState<StockStatus | ''>('');
  const [sort, setSort] = useState('updatedAt-desc');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewItem, setViewItem] = useState<Inventory | null>(null);
  const [historyItem, setHistoryItem] = useState<Inventory | null>(null);
  const [adjustItem, setAdjustItem] = useState<Inventory | null | undefined>(undefined);

  const [sortBy, sortDir] = sort.split('-') as [string, 'asc' | 'desc'];

  const loadCategories = async () => {
    try {
      const res = await categoryApi.list();
      setCategories(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load categories').message);
    }
  };

  const loadSummary = async () => {
    setSummaryLoading(true);
    try {
      const res = await inventoryApi.getSummary();
      setSummary(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load inventory summary').message);
    } finally {
      setSummaryLoading(false);
    }
  };

  const loadInventory = async () => {
    setLoading(true);
    try {
      const res = await inventoryApi.list({
        search,
        categoryId: categoryFilter ? Number(categoryFilter) : undefined,
        stockStatus: stockStatusFilter || undefined,
        page,
        size: PAGE_SIZE,
        sortBy,
        sortDir,
      });
      setItems(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load inventory').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCategories();
    loadSummary();
  }, []);

  useEffect(() => {
    loadInventory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, categoryFilter, stockStatusFilter, sortBy, sortDir]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadInventory();
  };

  const handleAdjusted = () => {
    setAdjustItem(undefined);
    loadInventory();
    loadSummary();
  };

  const summaryCards: SummaryCardData[] = [
    { key: 'total', label: 'Total Products', value: String(summary.totalProducts), icon: Package },
    { key: 'stock', label: 'Total Stock', value: String(summary.totalStockUnits), icon: Boxes },
    { key: 'low', label: 'Low Stock', value: String(summary.lowStockCount), icon: AlertTriangle },
    { key: 'out', label: 'Out of Stock', value: String(summary.outOfStockCount), icon: PackageX },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inventory"
        description="Track current stock levels and stock movements across your catalog."
        actions={
          canManage ? (
            <Button onClick={() => setAdjustItem(null)}>
              <SlidersHorizontal className="h-4 w-4" /> Adjust Stock
            </Button>
          ) : undefined
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {summaryLoading
          ? Array.from({ length: 4 }).map((_, i) => <SummaryCardSkeleton key={i} />)
          : summaryCards.map((card) => <SummaryCard key={card.key} data={card} />)}
      </div>

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <form onSubmit={handleSearchSubmit} className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by product name or SKU"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </form>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:flex lg:shrink-0">
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
              value={stockStatusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStockStatusFilter(v === ALL ? '' : (v as StockStatus));
              }}
            >
              <SelectTrigger className="w-full lg:w-36">
                <SelectValue placeholder="Stock Status" />
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
                <TableHead className="text-right">Current Stock</TableHead>
                <TableHead className="text-right">Minimum Stock</TableHead>
                <TableHead>Unit</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last Updated</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={9} />
            ) : (
              <TableBody>
                {items.map((inv) => (
                  <TableRow key={inv.id}>
                    <TableCell className="font-medium">{inv.productName}</TableCell>
                    <TableCell className="text-muted-foreground">{inv.sku || '—'}</TableCell>
                    <TableCell>{inv.categoryName || '—'}</TableCell>
                    <TableCell className="text-right font-medium">{inv.currentStock}</TableCell>
                    <TableCell className="text-right text-muted-foreground">{inv.minStockLevel}</TableCell>
                    <TableCell className="text-muted-foreground">{inv.unit}</TableCell>
                    <TableCell>
                      <Badge variant={stockStatusVariant(inv.stockStatus)}>{stockStatusLabel(inv.stockStatus)}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(inv.lastUpdated).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setViewItem(inv)}>
                            <Eye className="h-4 w-4" /> View
                          </DropdownMenuItem>
                          {canManage && (
                            <DropdownMenuItem onClick={() => setAdjustItem(inv)}>
                              <SlidersHorizontal className="h-4 w-4" /> Adjust Stock
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem onClick={() => setHistoryItem(inv)}>
                            <History className="h-4 w-4" /> View History
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && items.length === 0 && (
            <EmptyState
              icon={Boxes}
              title="No inventory records found"
              description="Try adjusting your search or filters. Inventory records are created automatically for each product."
            />
          )}

          {!loading && items.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <InventoryViewModal show={!!viewItem} inventory={viewItem} onClose={() => setViewItem(null)} />

      <StockHistoryModal show={!!historyItem} inventory={historyItem} onClose={() => setHistoryItem(null)} />

      <StockAdjustmentDialog
        show={adjustItem !== undefined}
        preselected={adjustItem || null}
        onClose={() => setAdjustItem(undefined)}
        onAdjusted={handleAdjusted}
      />
    </div>
  );
}
