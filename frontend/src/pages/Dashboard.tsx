import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Contact,
  Package,
  Receipt,
  ShoppingCart,
  Truck,
  Lock,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuth } from '@/context/AuthContext';
import { productApi } from '@/api/productApi';
import { customerApi } from '@/api/customerApi';
import { supplierApi } from '@/api/supplierApi';
import { saleApi } from '@/api/saleApi';
import { purchaseApi } from '@/api/purchaseApi';
import { Product } from '@/types/product';
import { Sale } from '@/types/sale';
import { getStockStatus } from '@/utils/stockStatus';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

const CHART_DAYS = 7;

function todayISO() {
  return new Date().toISOString().slice(0, 10);
}

function daysAgoISO(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

function buildDailyTotals(dates: string[], rows: { date: string; amount: number }[]) {
  const totals = new Map(dates.map((d) => [d, 0]));
  for (const row of rows) {
    if (totals.has(row.date)) {
      totals.set(row.date, (totals.get(row.date) ?? 0) + row.amount);
    }
  }
  return dates.map((date) => ({
    date,
    label: new Date(date + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    total: Math.round((totals.get(date) ?? 0) * 100) / 100,
  }));
}

interface StatCardData {
  key: string;
  label: string;
  value: string;
  hint: string;
  icon: typeof Package;
  restricted?: boolean;
}

function StatCard({ data }: { data: StatCardData }) {
  const Icon = data.icon;
  return (
    <Card className="overflow-hidden border-border/80 bg-gradient-to-br from-card to-muted/30 transition-shadow hover:shadow-md">
      <CardContent className="flex items-start justify-between gap-3 p-5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{data.label}</p>
          <div className="mt-1 flex items-center gap-1.5">
            <p className="text-2xl font-bold tracking-tight">{data.value}</p>
            {data.restricted && <Lock className="h-3.5 w-3.5 text-muted-foreground" />}
          </div>
          <p className="mt-1 truncate text-xs text-muted-foreground">{data.hint}</p>
        </div>
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  );
}

function StatCardSkeleton() {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-3 p-5">
        <div className="w-full space-y-2">
          <Skeleton className="h-3.5 w-24" />
          <Skeleton className="h-7 w-16" />
          <Skeleton className="h-3 w-20" />
        </div>
        <Skeleton className="h-10 w-10 rounded-lg" />
      </CardContent>
    </Card>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canSeePurchases = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState<Product[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);
  const [totalCustomers, setTotalCustomers] = useState(0);
  const [totalSuppliers, setTotalSuppliers] = useState(0);
  const [todaysSalesTotal, setTodaysSalesTotal] = useState(0);
  const [totalPurchasesCount, setTotalPurchasesCount] = useState<number | null>(null);
  const [recentSales, setRecentSales] = useState<Sale[]>([]);
  const [salesChartRows, setSalesChartRows] = useState<{ date: string; amount: number }[]>([]);
  const [purchaseChartRows, setPurchaseChartRows] = useState<{ date: string; amount: number }[]>([]);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      const today = todayISO();
      const from = daysAgoISO(CHART_DAYS - 1);

      const [productsRes, customersRes, suppliersRes, todaysSalesRes, recentSalesRes, chartSalesRes] =
        await Promise.allSettled([
          productApi.list({ size: 200 }),
          customerApi.list(),
          supplierApi.list(),
          saleApi.list({ fromDate: today, toDate: today, size: 200 }),
          saleApi.list({ size: 5 }),
          saleApi.list({ fromDate: from, toDate: today, size: 200 }),
        ]);

      const [purchaseCountRes, chartPurchaseRes] = canSeePurchases
        ? await Promise.allSettled([
            purchaseApi.list({ size: 1 }),
            purchaseApi.list({ fromDate: from, toDate: today, size: 200 }),
          ])
        : [null, null];

      if (cancelled) return;

      if (productsRes.status === 'fulfilled') {
        setProducts(productsRes.value.data.content);
        setTotalProducts(productsRes.value.data.totalElements);
      }
      if (customersRes.status === 'fulfilled') setTotalCustomers(customersRes.value.data.length);
      if (suppliersRes.status === 'fulfilled') setTotalSuppliers(suppliersRes.value.data.length);

      if (todaysSalesRes.status === 'fulfilled') {
        const sum = todaysSalesRes.value.data.content
          .filter((s) => s.status !== 'CANCELLED')
          .reduce((acc, s) => acc + s.totalAmount, 0);
        setTodaysSalesTotal(sum);
      }

      if (recentSalesRes.status === 'fulfilled') setRecentSales(recentSalesRes.value.data.content);

      if (chartSalesRes.status === 'fulfilled') {
        setSalesChartRows(
          chartSalesRes.value.data.content
            .filter((s) => s.status !== 'CANCELLED')
            .map((s) => ({ date: s.saleDate, amount: s.totalAmount }))
        );
      }

      if (purchaseCountRes && purchaseCountRes.status === 'fulfilled') {
        setTotalPurchasesCount(purchaseCountRes.value.data.totalElements);
      }

      if (chartPurchaseRes && chartPurchaseRes.status === 'fulfilled') {
        setPurchaseChartRows(
          chartPurchaseRes.value.data.content
            .filter((p) => p.status !== 'CANCELLED')
            .map((p) => ({ date: p.purchaseDate, amount: p.totalAmount }))
        );
      }

      setLoading(false);
    };

    load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSeePurchases]);

  const lowStockProducts = useMemo(
    () =>
      products
        .filter((p) => getStockStatus(p) !== 'IN_STOCK')
        .sort((a, b) => a.stockQuantity - b.stockQuantity),
    [products]
  );

  const chartDates = useMemo(() => {
    const dates: string[] = [];
    for (let i = CHART_DAYS - 1; i >= 0; i--) dates.push(daysAgoISO(i));
    return dates;
  }, []);

  const salesChartData = useMemo(() => buildDailyTotals(chartDates, salesChartRows), [chartDates, salesChartRows]);
  const purchaseChartData = useMemo(
    () => buildDailyTotals(chartDates, purchaseChartRows),
    [chartDates, purchaseChartRows]
  );

  const stats: StatCardData[] = [
    {
      key: 'products',
      label: 'Total Products',
      value: String(totalProducts),
      hint: 'In catalog',
      icon: Package,
    },
    {
      key: 'todaySales',
      label: "Today's Sales",
      value: todaysSalesTotal.toFixed(2),
      hint: new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      icon: Receipt,
    },
    {
      key: 'purchases',
      label: 'Total Purchases',
      value: canSeePurchases ? String(totalPurchasesCount ?? 0) : '—',
      hint: canSeePurchases ? 'All-time orders' : 'Restricted to managers',
      icon: ShoppingCart,
      restricted: !canSeePurchases,
    },
    {
      key: 'customers',
      label: 'Total Customers',
      value: String(totalCustomers),
      hint: 'Registered customers',
      icon: Contact,
    },
    {
      key: 'lowStock',
      label: 'Low Stock Products',
      value: String(lowStockProducts.length),
      hint: 'At or below minimum level',
      icon: AlertTriangle,
    },
    {
      key: 'suppliers',
      label: 'Total Suppliers',
      value: String(totalSuppliers),
      hint: 'Active suppliers',
      icon: Truck,
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Welcome back{user ? `, ${user.firstName}` : ''}</h2>
        <p className="text-sm text-muted-foreground">Here&apos;s what&apos;s happening in your store today.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {loading
          ? Array.from({ length: 6 }).map((_, i) => <StatCardSkeleton key={i} />)
          : stats.map((stat) =>
              stat.restricted ? (
                <Tooltip key={stat.key}>
                  <TooltipTrigger asChild>
                    <div>
                      <StatCard data={stat} />
                    </div>
                  </TooltipTrigger>
                  <TooltipContent>Only visible to Admin and Store Manager</TooltipContent>
                </Tooltip>
              ) : (
                <StatCard key={stat.key} data={stat} />
              )
            )}
      </div>

      <div className={`grid grid-cols-1 gap-4 ${canSeePurchases ? 'lg:grid-cols-2' : ''}`}>
        <Card>
          <CardHeader>
            <CardTitle>Sales Overview</CardTitle>
            <p className="text-xs text-muted-foreground">Last {CHART_DAYS} days</p>
          </CardHeader>
          <CardContent className="pl-1">
            {loading ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={salesChartData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                  <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} width={40} />
                  <RechartsTooltip
                    cursor={{ fill: 'hsl(var(--muted))' }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--popover))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: 8,
                      fontSize: 12,
                      color: 'hsl(var(--popover-foreground))',
                    }}
                    formatter={(value) => [Number(value).toFixed(2), 'Sales']}
                  />
                  <Bar dataKey="total" fill="hsl(var(--chart-1))" radius={[4, 4, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        {canSeePurchases && (
          <Card>
            <CardHeader>
              <CardTitle>Purchase Overview</CardTitle>
              <p className="text-xs text-muted-foreground">Last {CHART_DAYS} days</p>
            </CardHeader>
            <CardContent className="pl-1">
              {loading ? (
                <Skeleton className="h-64 w-full" />
              ) : (
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={purchaseChartData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-border" vertical={false} />
                    <XAxis dataKey="label" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                    <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} width={40} />
                    <RechartsTooltip
                      cursor={{ fill: 'hsl(var(--muted))' }}
                      contentStyle={{
                        backgroundColor: 'hsl(var(--popover))',
                        border: '1px solid hsl(var(--border))',
                        borderRadius: 8,
                        fontSize: 12,
                        color: 'hsl(var(--popover-foreground))',
                      }}
                      formatter={(value) => [Number(value).toFixed(2), 'Purchases']}
                    />
                    <Bar dataKey="total" fill="hsl(var(--chart-2))" radius={[4, 4, 0, 0]} maxBarSize={40} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </CardContent>
          </Card>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Recent Sales</CardTitle>
            <Button variant="ghost" size="sm" onClick={() => navigate('/sales')} className="gap-1 text-xs">
              View all <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : recentSales.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">No sales recorded yet.</div>
            ) : (
              <div className="divide-y divide-border">
                {recentSales.map((sale) => (
                  <div key={sale.id} className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{sale.invoiceNumber}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {sale.customer ? `${sale.customer.firstName} ${sale.customer.lastName || ''}` : 'Walk-in'} ·{' '}
                        {sale.saleDate}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-semibold">{sale.totalAmount.toFixed(2)}</p>
                      <Badge
                        variant={
                          sale.status === 'COMPLETED' ? 'success' : sale.status === 'CANCELLED' ? 'destructive' : 'muted'
                        }
                        className="mt-0.5"
                      >
                        {sale.status}
                      </Badge>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Low Stock Products</CardTitle>
            <Badge variant="warning">{lowStockProducts.length} items</Badge>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : lowStockProducts.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                All products are well stocked.
              </div>
            ) : (
              <div className="divide-y divide-border">
                {lowStockProducts.slice(0, 6).map((product) => (
                  <div key={product.id} className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{product.name}</p>
                      <p className="text-xs text-muted-foreground">{product.unit}</p>
                    </div>
                    <Badge variant={product.stockQuantity === 0 ? 'destructive' : 'warning'}>
                      {product.stockQuantity} left
                    </Badge>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
