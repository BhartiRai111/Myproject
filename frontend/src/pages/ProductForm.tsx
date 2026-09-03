import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus } from 'lucide-react';
import CategoryQuickAddModal from '../components/CategoryQuickAddModal';
import { productApi } from '../api/productApi';
import { categoryApi } from '../api/categoryApi';
import { parseApiError } from '../utils/apiError';
import { Category, Product } from '../types/product';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export default function ProductForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [brand, setBrand] = useState('');
  const [unit, setUnit] = useState('pcs');
  const [purchasePrice, setPurchasePrice] = useState('0');
  const [sellingPrice, setSellingPrice] = useState('0');
  const [tax, setTax] = useState('0');
  const [minStockLevel, setMinStockLevel] = useState('0');
  const [description, setDescription] = useState('');

  const [showCategoryModal, setShowCategoryModal] = useState(false);

  useEffect(() => {
    const loadCategories = async () => {
      const res = await categoryApi.list();
      setCategories(res.data.filter((c) => c.status === 'ACTIVE'));
    };

    const loadProduct = async () => {
      if (!isEdit) return;
      const res = await productApi.getById(Number(id));
      const product: Product = res.data;
      setName(product.name);
      setSku(product.sku);
      setBarcode(product.barcode || '');
      setCategoryId(String(product.categoryId));
      setBrand(product.brand || '');
      setUnit(product.unit);
      setPurchasePrice(String(product.purchasePrice));
      setSellingPrice(String(product.sellingPrice));
      setTax(String(product.tax));
      setMinStockLevel(String(product.minStockLevel));
      setDescription(product.description || '');
    };

    Promise.all([loadCategories(), loadProduct()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const validate = (): string | null => {
    if (!name.trim()) return 'Product name is required';
    if (!sku.trim()) return 'SKU is required';
    if (!categoryId) return 'Category is required';
    if (toNumber(purchasePrice) < 0) return 'Purchase price must be greater than or equal to 0';
    if (toNumber(sellingPrice) < 0) return 'Selling price must be greater than or equal to 0';
    if (toNumber(tax) < 0) return 'Tax must be greater than or equal to 0';
    if (toNumber(minStockLevel) < 0) return 'Minimum stock level cannot be negative';
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

    const payload = {
      name,
      sku,
      barcode: barcode || undefined,
      categoryId: Number(categoryId),
      brand: brand || undefined,
      unit,
      purchasePrice: toNumber(purchasePrice),
      sellingPrice: toNumber(sellingPrice),
      tax: toNumber(tax),
      minStockLevel: toNumber(minStockLevel),
      description: description || undefined,
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await productApi.update(Number(id), payload);
        toast.success('Product updated successfully');
      } else {
        await productApi.create(payload);
        toast.success('Product created successfully');
      }
      navigate('/products');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save product');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEdit ? 'Edit Product' : 'Add Product'}
        description="Manage catalog details, pricing, and stock thresholds."
      />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Basic Info</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="name">Product Name</Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                invalid={!!fieldErrors.name}
              />
              {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="sku">SKU</Label>
              <Input id="sku" value={sku} onChange={(e) => setSku(e.target.value)} invalid={!!fieldErrors.sku} />
              {fieldErrors.sku && <p className="text-xs text-destructive">{fieldErrors.sku}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="barcode">Barcode</Label>
              <Input
                id="barcode"
                value={barcode}
                onChange={(e) => setBarcode(e.target.value)}
                invalid={!!fieldErrors.barcode}
              />
              {fieldErrors.barcode && <p className="text-xs text-destructive">{fieldErrors.barcode}</p>}
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
              <Label htmlFor="brand">Brand</Label>
              <Input id="brand" value={brand} onChange={(e) => setBrand(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="unit">Unit</Label>
              <Input id="unit" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="pcs, kg, box..." />
            </div>

            <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Pricing</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="purchasePrice">Purchase Price</Label>
              <Input
                id="purchasePrice"
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
              <Label htmlFor="sellingPrice">Selling Price</Label>
              <Input
                id="sellingPrice"
                type="number"
                min={0}
                step="0.01"
                value={sellingPrice}
                onChange={(e) => setSellingPrice(e.target.value)}
                invalid={!!fieldErrors.sellingPrice}
              />
              {fieldErrors.sellingPrice && <p className="text-xs text-destructive">{fieldErrors.sellingPrice}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="tax">Tax (%)</Label>
              <Input
                id="tax"
                type="number"
                min={0}
                step="0.01"
                value={tax}
                onChange={(e) => setTax(e.target.value)}
                invalid={!!fieldErrors.tax}
              />
              {fieldErrors.tax && <p className="text-xs text-destructive">{fieldErrors.tax}</p>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Inventory</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="minStockLevel">Minimum Stock Level</Label>
              <Input
                id="minStockLevel"
                type="number"
                min={0}
                value={minStockLevel}
                onChange={(e) => setMinStockLevel(e.target.value)}
                invalid={!!fieldErrors.minStockLevel}
              />
              {fieldErrors.minStockLevel && <p className="text-xs text-destructive">{fieldErrors.minStockLevel}</p>}
            </div>
            {isEdit && (
              <div className="space-y-1.5">
                <Label>Current Stock</Label>
                <p className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
                  Managed via Purchases &amp; Sales
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            {isEdit ? 'Save Changes' : 'Create Product'}
          </Button>
          <Button type="button" variant="outline" onClick={() => navigate('/products')}>
            Cancel
          </Button>
        </div>
      </form>

      <CategoryQuickAddModal
        show={showCategoryModal}
        onClose={() => setShowCategoryModal(false)}
        onCreated={(category) => {
          setCategories((prev) => [...prev, category]);
          setShowCategoryModal(false);
          setTimeout(() => setCategoryId(String(category.id)), 0);
          toast.success('Category added');
        }}
      />
    </div>
  );
}
