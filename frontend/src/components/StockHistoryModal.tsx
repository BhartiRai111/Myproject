import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { inventoryApi } from '../api/inventoryApi';
import { parseApiError } from '../utils/apiError';
import { Inventory, ReferenceType, StockHistory, StockMovementType } from '../types/inventory';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  show: boolean;
  inventory: Inventory | null;
  onClose: () => void;
}

const PAGE_SIZE = 10;
const ALL = '__all__';

const MOVEMENT_TYPES: StockMovementType[] = [
  'PURCHASE',
  'SALE',
  'STOCK_IN',
  'STOCK_OUT',
  'ADJUSTMENT',
  'SALE_CANCEL',
  'PURCHASE_CANCEL',
];

const REFERENCE_TYPES: ReferenceType[] = ['PURCHASE', 'SALE', 'MANUAL'];

function movementVariant(type: StockMovementType) {
  if (type === 'PURCHASE' || type === 'STOCK_IN' || type === 'SALE_CANCEL') return 'success' as const;
  if (type === 'SALE' || type === 'STOCK_OUT' || type === 'PURCHASE_CANCEL') return 'destructive' as const;
  return 'muted' as const;
}

export default function StockHistoryModal({ show, inventory, onClose }: Props) {
  const [history, setHistory] = useState<StockHistory[]>([]);
  const [loading, setLoading] = useState(false);
  const [movementType, setMovementType] = useState<StockMovementType | ''>('');
  const [referenceType, setReferenceType] = useState<ReferenceType | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const loadHistory = async () => {
    if (!inventory) return;
    setLoading(true);
    try {
      const res = await inventoryApi.getHistory({
        productId: inventory.productId,
        movementType: movementType || undefined,
        referenceType: referenceType || undefined,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        page,
        size: PAGE_SIZE,
      });
      setHistory(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load stock history').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (show) {
      setMovementType('');
      setReferenceType('');
      setFromDate('');
      setToDate('');
      setPage(0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show, inventory?.id]);

  useEffect(() => {
    if (show) {
      loadHistory();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show, inventory?.id, movementType, referenceType, fromDate, toDate, page]);

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Stock History</DialogTitle>
          <DialogDescription>{inventory?.productName}</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Select
            value={movementType || ALL}
            onValueChange={(v) => {
              setPage(0);
              setMovementType(v === ALL ? '' : (v as StockMovementType));
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Movement Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All Types</SelectItem>
              {MOVEMENT_TYPES.map((type) => (
                <SelectItem key={type} value={type}>
                  {type.replace('_', ' ')}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={referenceType || ALL}
            onValueChange={(v) => {
              setPage(0);
              setReferenceType(v === ALL ? '' : (v as ReferenceType));
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Reference" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All References</SelectItem>
              {REFERENCE_TYPES.map((type) => (
                <SelectItem key={type} value={type}>
                  {type}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => {
              setPage(0);
              setFromDate(e.target.value);
            }}
            className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
          />
          <input
            type="date"
            value={toDate}
            onChange={(e) => {
              setPage(0);
              setToDate(e.target.value);
            }}
            className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
          />
        </div>

        <div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Date</TableHead>
                <TableHead>Movement</TableHead>
                <TableHead className="text-right">Quantity</TableHead>
                <TableHead className="text-right">Previous</TableHead>
                <TableHead className="text-right">New</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Reference</TableHead>
                <TableHead>By</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={8}>
                    <div className="space-y-2 py-2">
                      <Skeleton className="h-6 w-full" />
                      <Skeleton className="h-6 w-full" />
                      <Skeleton className="h-6 w-full" />
                    </div>
                  </TableCell>
                </TableRow>
              ) : history.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-sm text-muted-foreground">
                    No stock movements recorded yet.
                  </TableCell>
                </TableRow>
              ) : (
                history.map((h) => (
                  <TableRow key={h.id}>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {new Date(h.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Badge variant={movementVariant(h.movementType)}>{h.movementType.replace('_', ' ')}</Badge>
                    </TableCell>
                    <TableCell className="text-right">{h.quantity}</TableCell>
                    <TableCell className="text-right text-muted-foreground">{h.previousStock}</TableCell>
                    <TableCell className="text-right font-medium">{h.newStock}</TableCell>
                    <TableCell className="max-w-[160px] truncate">{h.reason || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {h.referenceType ? `${h.referenceType}${h.referenceId ? ' #' + h.referenceId : ''}` : '—'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{h.createdBy || '—'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        {!loading && history.length > 0 && <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />}
      </DialogContent>
    </Dialog>
  );
}
