import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Building2 } from 'lucide-react';
import { storeComparisonApi } from '../../../api/storeComparisonApi';
import { parseApiError } from '../../../utils/apiError';
import { StoreComparisonResponse } from '../../../types/storeComparison';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, firstDayOfMonthIso, todayIso } from './reportFormat';

export default function StoreComparison() {
  const navigate = useNavigate();
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso());
  const [toDate, setToDate] = useState(todayIso());
  const [report, setReport] = useState<StoreComparisonResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await storeComparisonApi.compare(fromDate, toDate);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load Store Comparison').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rows = report?.rows ?? [];
  const totalSales = rows.reduce((sum, r) => sum + r.totalSales, 0);
  const totalPurchases = rows.reduce((sum, r) => sum + r.totalPurchases, 0);
  const totalStockUnits = rows.reduce((sum, r) => sum + r.totalStockUnits, 0);

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Store Comparison"
        description="Sales, purchases and stock position across every store you have access to, for a date range."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label htmlFor="fromDate" className="text-xs text-muted-foreground">From</Label>
              <Input id="fromDate" type="date" className="w-40" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </div>
            <div>
              <Label htmlFor="toDate" className="text-xs text-muted-foreground">To</Label>
              <Input id="toDate" type="date" className="w-40" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </div>
            <Button type="button" onClick={load}>Apply</Button>
          </div>
        }
      />

      {!loading && rows.length > 0 && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Stores Compared</p>
              <p className="text-lg font-bold">{rows.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Sales</p>
              <p className="text-lg font-bold">{money(totalSales)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Purchases</p>
              <p className="text-lg font-bold">{money(totalPurchases)}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4">
              <p className="text-xs text-muted-foreground">Total Stock Units</p>
              <p className="text-lg font-bold">{totalStockUnits.toLocaleString('en-IN')}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Store</TableHead>
                <TableHead className="text-right">Sales</TableHead>
                <TableHead className="text-right">Sales Count</TableHead>
                <TableHead className="text-right">Purchases</TableHead>
                <TableHead className="text-right">Purchase Count</TableHead>
                <TableHead className="text-right">Stock Units</TableHead>
                <TableHead className="text-right">Low Stock</TableHead>
                <TableHead className="text-right">Out of Stock</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.storeId}>
                    <TableCell className="font-medium">
                      {r.storeName} <span className="text-xs text-muted-foreground">({r.storeCode})</span>
                    </TableCell>
                    <TableCell className="text-right">{money(r.totalSales)}</TableCell>
                    <TableCell className="text-right">{r.salesCount}</TableCell>
                    <TableCell className="text-right">{money(r.totalPurchases)}</TableCell>
                    <TableCell className="text-right">{r.purchaseCount}</TableCell>
                    <TableCell className="text-right">{r.totalStockUnits.toLocaleString('en-IN')}</TableCell>
                    <TableCell className="text-right">
                      {r.lowStockCount > 0 ? <Badge variant="warning">{r.lowStockCount}</Badge> : '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      {r.outOfStockCount > 0 ? <Badge variant="destructive">{r.outOfStockCount}</Badge> : '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && rows.length === 0 && (
            <EmptyState
              icon={Building2}
              title="No stores to compare"
              description="You don't have access to more than one store, or no stores are set up yet."
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
