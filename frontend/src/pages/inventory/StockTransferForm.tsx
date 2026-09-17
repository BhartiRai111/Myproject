import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import { productApi } from '../../api/productApi';
import { storeApi } from '../../api/storeApi';
import { stockTransferApi } from '../../api/stockTransferApi';
import { parseApiError } from '../../utils/apiError';
import { Product } from '../../types/product';
import { Store } from '../../types/store';
import { StockTransferItemPayload } from '../../types/stockTransfer';
import { useAuth } from '../../context/AuthContext';
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface ItemRow {
  productId: string;
  quantity: string;
  notes: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', notes: '' };

export default function StockTransferForm() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [stores, setStores] = useState<Store[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [transferDate, setTransferDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [fromStoreId, setFromStoreId] = useState('');
  const [toStoreId, setToStoreId] = useState('');
  const [remarks, setRemarks] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () => JSON.stringify({ transferDate, fromStoreId, toStoreId, remarks, items });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    Promise.all([storeApi.list({ status: 'ACTIVE', size: 1000 }), productApi.list({ size: 200, status: 'ACTIVE' })])
      .then(([storeRes, productRes]) => {
        setStores(storeRes.data.content);
        setProducts(productRes.data.content);
        if (user?.currentStoreId) {
          setFromStoreId(String(user.currentStoreId));
        }
      })
      .catch((err) => toast.error(parseApiError(err, 'Failed to load reference data').message))
      .finally(() => {
        setLoading(false);
        initialSnapshot.current = getSnapshot();
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateItem = (index: number, patch: Partial<ItemRow>) => {
    setItems((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const addItem = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItem = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const validate = (): string | null => {
    if (!fromStoreId) return 'Source store is required';
    if (!toStoreId) return 'Destination store is required';
    if (fromStoreId === toStoreId) return 'Source and destination store must be different';
    if (items.length === 0 || items.every((r) => !r.productId)) return 'At least one item is required';
    for (const row of items) {
      if (row.productId && (!row.quantity || Number(row.quantity) <= 0)) {
        return 'Every item must have a quantity greater than 0';
      }
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const payloadItems: StockTransferItemPayload[] = items
      .filter((row) => row.productId)
      .map((row) => ({ productId: Number(row.productId), quantity: Number(row.quantity), notes: row.notes || undefined }));

    setSubmitting(true);
    try {
      const res = await stockTransferApi.create({
        transferDate,
        fromStoreId: Number(fromStoreId),
        toStoreId: Number(toStoreId),
        remarks: remarks || undefined,
        items: payloadItems,
      });
      toast.success(`Stock transfer ${res.data.transferNumber} created`);
      initialSnapshot.current = getSnapshot();
      navigate(`/inventory/stock-transfers/${res.data.id}`);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to create stock transfer');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-6">
      <BackButton label="Back to Stock Transfers" onClick={() => guardedNavigate('/inventory/stock-transfers')} />

      <PageHeader title="New Stock Transfer" description="Move stock from one store to another." />

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Card>
          <CardHeader>
            <CardTitle>Transfer Details</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="transferDate">Transfer Date</Label>
              <Input
                id="transferDate"
                type="date"
                required
                value={transferDate}
                onChange={(e) => setTransferDate(e.target.value)}
                invalid={!!fieldErrors.transferDate}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fromStore">From Store</Label>
              <Select value={fromStoreId} onValueChange={(v) => v && setFromStoreId(v)}>
                <SelectTrigger id="fromStore" invalid={!!fieldErrors.fromStoreId}>
                  <SelectValue placeholder="Select source store" />
                </SelectTrigger>
                <SelectContent>
                  {stores.map((s) => (
                    <SelectItem key={s.id} value={String(s.id)}>
                      {s.storeName} ({s.storeCode})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {fieldErrors.fromStoreId && <p className="text-xs text-destructive">{fieldErrors.fromStoreId}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="toStore">To Store</Label>
              <Select value={toStoreId} onValueChange={(v) => v && setToStoreId(v)}>
                <SelectTrigger id="toStore" invalid={!!fieldErrors.toStoreId}>
                  <SelectValue placeholder="Select destination store" />
                </SelectTrigger>
                <SelectContent>
                  {stores
                    .filter((s) => String(s.id) !== fromStoreId)
                    .map((s) => (
                      <SelectItem key={s.id} value={String(s.id)}>
                        {s.storeName} ({s.storeCode})
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
              {fieldErrors.toStoreId && <p className="text-xs text-destructive">{fieldErrors.toStoreId}</p>}
            </div>
            <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
              <Label htmlFor="remarks">Remarks</Label>
              <Textarea id="remarks" rows={2} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Items</CardTitle>
            <Button type="button" variant="outline" size="sm" onClick={addItem}>
              <Plus className="h-4 w-4" /> Add Item
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead className="w-32 text-right">Quantity</TableHead>
                  <TableHead>Notes</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((row, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      <Select value={row.productId} onValueChange={(v) => v && updateItem(index, { productId: v })}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select product" />
                        </SelectTrigger>
                        <SelectContent>
                          {products.map((p) => (
                            <SelectItem key={p.id} value={String(p.id)}>
                              {p.name} {p.sku ? `(${p.sku})` : ''}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <Input
                        type="number"
                        min={1}
                        className="text-right"
                        value={row.quantity}
                        onChange={(e) => updateItem(index, { quantity: e.target.value })}
                      />
                    </TableCell>
                    <TableCell>
                      <Input value={row.notes} onChange={(e) => updateItem(index, { notes: e.target.value })} />
                    </TableCell>
                    <TableCell>
                      {items.length > 1 && (
                        <Button type="button" variant="ghost" size="icon" onClick={() => removeItem(index)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={() => guardedNavigate('/inventory/stock-transfers')}>
            Cancel
          </Button>
          <Button type="submit" loading={submitting}>
            Create Transfer
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this stock transfer. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
