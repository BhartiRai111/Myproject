import { useEffect, useState } from 'react';
import { productApi } from '../api/productApi';
import { parseApiError } from '../utils/apiError';
import { Product } from '../types/purchase';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
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
  onClose: () => void;
  onCreated: (product: Product) => void;
}

export default function ProductQuickAddModal({ show, onClose, onCreated }: Props) {
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('pcs');
  const [sellingPrice, setSellingPrice] = useState('0');
  const [stockQuantity, setStockQuantity] = useState('0');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setUnit('pcs');
      setSellingPrice('0');
      setStockQuantity('0');
      setError('');
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await productApi.create({
        name,
        unit,
        sellingPrice: Number(sellingPrice) || 0,
        stockQuantity: Number(stockQuantity) || 0,
      });
      onCreated(res.data);
    } catch (err) {
      setError(parseApiError(err, 'Failed to add product').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Add Product</DialogTitle>
          <DialogDescription>Add a new product to your catalog.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="pqName">Name</Label>
            <Input id="pqName" required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="pqUnit">Unit</Label>
            <Input id="pqUnit" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="pcs, kg, box..." />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="pqPrice">Selling Price</Label>
              <Input
                id="pqPrice"
                type="number"
                min={0}
                step="0.01"
                value={sellingPrice}
                onChange={(e) => setSellingPrice(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pqStock">Opening Stock</Label>
              <Input
                id="pqStock"
                type="number"
                min={0}
                value={stockQuantity}
                onChange={(e) => setStockQuantity(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Add Product
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
