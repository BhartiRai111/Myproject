import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { AlertTriangle } from 'lucide-react';
import { inventoryApi } from '../api/inventoryApi';
import { parseApiError } from '../utils/apiError';
import { Inventory, ManualAdjustmentType } from '../types/inventory';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

interface Props {
  show: boolean;
  preselected: Inventory | null;
  onClose: () => void;
  onAdjusted: () => void;
}

const ADJUSTMENT_TYPES: { value: ManualAdjustmentType; label: string; hint: string }[] = [
  { value: 'STOCK_IN', label: 'Stock In', hint: 'Add quantity to current stock' },
  { value: 'STOCK_OUT', label: 'Stock Out', hint: 'Remove quantity from current stock' },
  { value: 'ADJUSTMENT', label: 'Adjustment', hint: 'Correct stock to a known/counted quantity' },
];

export default function StockAdjustmentDialog({ show, preselected, onClose, onAdjusted }: Props) {
  const [inventoryOptions, setInventoryOptions] = useState<Inventory[]>([]);
  const [productId, setProductId] = useState('');
  const [movementType, setMovementType] = useState<ManualAdjustmentType>('STOCK_IN');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [notes, setNotes] = useState('');
  const [step, setStep] = useState<'form' | 'confirm'>('form');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!show) return;
    setStep('form');
    setError('');
    setFieldErrors({});
    setMovementType('STOCK_IN');
    setQuantity('');
    setReason('');
    setNotes('');

    if (preselected) {
      setProductId(String(preselected.productId));
      setInventoryOptions([preselected]);
    } else {
      setProductId('');
      inventoryApi
        .list({ size: 200 })
        .then((res) => setInventoryOptions(res.data.content))
        .catch((err) => toast.error(parseApiError(err, 'Failed to load products').message));
    }
  }, [show, preselected]);

  const selected = inventoryOptions.find((inv) => String(inv.productId) === productId) || preselected || null;
  const currentStock = selected?.currentStock ?? 0;
  const quantityNum = Number(quantity) || 0;

  const computeNewStock = (): number | null => {
    if (!quantityNum || quantityNum <= 0) return null;
    if (movementType === 'STOCK_IN') return currentStock + quantityNum;
    if (movementType === 'STOCK_OUT') return currentStock - quantityNum;
    return quantityNum;
  };

  const newStock = computeNewStock();

  const validate = (): string | null => {
    if (!productId) return 'Product is required';
    if (!quantityNum || quantityNum <= 0) return 'Quantity must be greater than 0';
    if (!reason.trim()) return 'Reason is required';
    if (newStock !== null && newStock < 0) {
      return `Stock cannot go negative: current stock is ${currentStock}, this would result in ${newStock}`;
    }
    return null;
  };

  const handleReview = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setStep('confirm');
  };

  const handleConfirm = async () => {
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    try {
      await inventoryApi.adjust({
        productId: Number(productId),
        movementType,
        quantity: quantityNum,
        reason,
        notes: notes || undefined,
      });
      toast.success('Stock adjustment applied successfully');
      onAdjusted();
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to adjust stock');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
      setStep('form');
    } finally {
      setSubmitting(false);
    }
  };

  const showLowStockWarning =
    movementType === 'STOCK_OUT' && newStock !== null && newStock >= 0 && currentStock > 0 && newStock / currentStock <= 0.2;

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Adjust Stock</DialogTitle>
          <DialogDescription>Manually record a stock movement for a product.</DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {step === 'form' && (
          <form onSubmit={handleReview} className="space-y-4" noValidate>
            <div className="space-y-1.5">
              <Label>Product</Label>
              <Select value={productId} onValueChange={(v) => v && setProductId(v)} disabled={!!preselected}>
                <SelectTrigger className={fieldErrors.productId ? 'border-destructive' : ''}>
                  <SelectValue placeholder="Select product" />
                </SelectTrigger>
                <SelectContent>
                  {inventoryOptions.map((inv) => (
                    <SelectItem key={inv.productId} value={String(inv.productId)}>
                      {inv.productName} ({inv.currentStock} {inv.unit})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {fieldErrors.productId && <p className="text-xs text-destructive">{fieldErrors.productId}</p>}
            </div>

            {selected && (
              <p className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
                Current stock: <span className="font-medium text-foreground">{currentStock}</span> {selected.unit}
              </p>
            )}

            <div className="space-y-1.5">
              <Label>Adjustment Type</Label>
              <Select value={movementType} onValueChange={(v) => setMovementType(v as ManualAdjustmentType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ADJUSTMENT_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                {ADJUSTMENT_TYPES.find((t) => t.value === movementType)?.hint}
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="quantity">{movementType === 'ADJUSTMENT' ? 'Corrected Stock Quantity' : 'Quantity'}</Label>
              <Input
                id="quantity"
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                invalid={!!fieldErrors.quantity}
              />
              {fieldErrors.quantity && <p className="text-xs text-destructive">{fieldErrors.quantity}</p>}
              {newStock !== null && (
                <p className={`text-xs ${newStock < 0 ? 'text-destructive' : 'text-muted-foreground'}`}>
                  New stock will be: <span className="font-medium">{newStock}</span>
                </p>
              )}
              {showLowStockWarning && (
                <p className="flex items-center gap-1 text-xs text-warning">
                  <AlertTriangle className="h-3.5 w-3.5" /> This will leave very little stock remaining.
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="reason">Reason</Label>
              <Input
                id="reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="e.g. Physical stock count, Damaged goods"
                invalid={!!fieldErrors.reason}
              />
              {fieldErrors.reason && <p className="text-xs text-destructive">{fieldErrors.reason}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="notes">Notes</Label>
              <Textarea id="notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit">Review Adjustment</Button>
            </DialogFooter>
          </form>
        )}

        {step === 'confirm' && selected && (
          <div className="space-y-4">
            <div className="rounded-lg border border-border p-4">
              <p className="text-sm font-medium">{selected.productName}</p>
              <div className="mt-3 flex items-center justify-between text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">Current Stock</p>
                  <p className="text-lg font-semibold">{currentStock}</p>
                </div>
                <span className="text-muted-foreground">&rarr;</span>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">New Stock</p>
                  <p className="text-lg font-semibold">{newStock}</p>
                </div>
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                {ADJUSTMENT_TYPES.find((t) => t.value === movementType)?.label} of {quantityNum} {selected.unit} &mdash;{' '}
                {reason}
              </p>
            </div>

            {showLowStockWarning && (
              <Alert variant="warning">
                <AlertTriangle className="h-4 w-4" />
                <AlertDescription>This adjustment will leave very little stock remaining.</AlertDescription>
              </Alert>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStep('form')} disabled={submitting}>
                Back
              </Button>
              <Button type="button" loading={submitting} onClick={handleConfirm}>
                Confirm Adjustment
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
