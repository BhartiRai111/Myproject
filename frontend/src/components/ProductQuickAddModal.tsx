import { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { productApi } from '../api/productApi';
import { categoryApi } from '../api/categoryApi';
import { parseApiError } from '../utils/apiError';
import { Product, Category } from '../types/product';
import CategoryQuickAddModal from './CategoryQuickAddModal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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
  onClose: () => void;
  onCreated: (product: Product) => void;
}

export default function ProductQuickAddModal({ show, onClose, onCreated }: Props) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [unit, setUnit] = useState('pcs');
  const [purchasePrice, setPurchasePrice] = useState('0');
  const [sellingPrice, setSellingPrice] = useState('0');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [showCategoryModal, setShowCategoryModal] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setSku('');
      setCategoryId('');
      setUnit('pcs');
      setPurchasePrice('0');
      setSellingPrice('0');
      setError('');
      setFieldErrors({});
      categoryApi.list().then((res) => setCategories(res.data.filter((c) => c.status === 'ACTIVE')));
    }
  }, [show]);

  const suggestSku = () => {
    if (!name.trim()) return;
    const base = name.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '-').slice(0, 20);
    setSku(`${base}-${Date.now().toString().slice(-4)}`);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    try {
      const res = await productApi.create({
        name,
        sku,
        categoryId: Number(categoryId),
        unit,
        purchasePrice: Number(purchasePrice) || 0,
        sellingPrice: Number(sellingPrice) || 0,
      });
      onCreated(res.data);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to add product');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
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
            <Input
              id="pqName"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="pqSku">SKU</Label>
            <div className="flex gap-2">
              <Input
                id="pqSku"
                required
                value={sku}
                onChange={(e) => setSku(e.target.value)}
                invalid={!!fieldErrors.sku}
              />
              <Button type="button" variant="outline" onClick={suggestSku}>
                Generate
              </Button>
            </div>
            {fieldErrors.sku && <p className="text-xs text-destructive">{fieldErrors.sku}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>Category</Label>
            <div className="flex gap-2">
              <Select value={categoryId} onValueChange={setCategoryId}>
                <SelectTrigger className={fieldErrors.categoryId ? 'border-destructive' : ''}>
                  <SelectValue placeholder="Select category" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button type="button" variant="outline" size="icon" onClick={() => setShowCategoryModal(true)}>
                <Plus className="h-4 w-4" />
              </Button>
            </div>
            {fieldErrors.categoryId && <p className="text-xs text-destructive">{fieldErrors.categoryId}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="pqUnit">Unit</Label>
            <Input id="pqUnit" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="pcs, kg, box..." />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="pqPurchasePrice">Purchase Price</Label>
              <Input
                id="pqPurchasePrice"
                type="number"
                min={0}
                step="0.01"
                value={purchasePrice}
                onChange={(e) => setPurchasePrice(e.target.value)}
                invalid={!!fieldErrors.purchasePrice}
              />
              {fieldErrors.purchasePrice && <p className="text-xs text-destructive">{fieldErrors.purchasePrice}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pqSellingPrice">Selling Price</Label>
              <Input
                id="pqSellingPrice"
                type="number"
                min={0}
                step="0.01"
                value={sellingPrice}
                onChange={(e) => setSellingPrice(e.target.value)}
                invalid={!!fieldErrors.sellingPrice}
              />
              {fieldErrors.sellingPrice && <p className="text-xs text-destructive">{fieldErrors.sellingPrice}</p>}
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

      <CategoryQuickAddModal
        show={showCategoryModal}
        onClose={() => setShowCategoryModal(false)}
        onCreated={(category) => {
          setCategories((prev) => [...prev, category]);
          setShowCategoryModal(false);
          setTimeout(() => setCategoryId(String(category.id)), 0);
        }}
      />
    </Dialog>
  );
}
