import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus } from 'lucide-react';
import CategoryQuickAddModal from '../components/CategoryQuickAddModal';
import { productApi } from '../api/productApi';
import { categoryApi } from '../api/categoryApi';
import { hsnApi, itemGroupApi, unitApi } from '../api/mastersApi';
import { parseApiError } from '../utils/apiError';
import { Category, Product } from '../types/product';
import { Hsn, ItemGroup, Unit } from '../types/masters';
import { useUnsavedChangesGuard } from '../hooks/useUnsavedChangesGuard';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
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
  const [itemGroups, setItemGroups] = useState<ItemGroup[]>([]);
  const [hsnCodes, setHsnCodes] = useState<Hsn[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
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

  const [manualCode, setManualCode] = useState('');
  const [itemGroupId, setItemGroupId] = useState('');
  const [hsnId, setHsnId] = useState('');
  const [purchaseUnitId, setPurchaseUnitId] = useState('');
  const [saleUnitId, setSaleUnitId] = useState('');
  const [tolerancePercent, setTolerancePercent] = useState('');
  const [itemType, setItemType] = useState('');
  const [taxNature, setTaxNature] = useState('');
  const [taxBasedOn, setTaxBasedOn] = useState('');
  const [partyName, setPartyName] = useState('');
  const [partyProductName, setPartyProductName] = useState('');
  const [freeValue, setFreeValue] = useState('');
  const [applicableProperty, setApplicableProperty] = useState('');

  const [showCategoryModal, setShowCategoryModal] = useState(false);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () =>
    JSON.stringify({
      name,
      sku,
      barcode,
      categoryId,
      brand,
      unit,
      purchasePrice,
      sellingPrice,
      tax,
      minStockLevel,
      description,
      manualCode,
      itemGroupId,
      hsnId,
      purchaseUnitId,
      saleUnitId,
      tolerancePercent,
      itemType,
      taxNature,
      taxBasedOn,
      partyName,
      partyProductName,
      freeValue,
      applicableProperty,
    });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    const loadCategories = async () => {
      const res = await categoryApi.list();
      setCategories(res.data.filter((c) => c.status === 'ACTIVE'));
    };

    const loadItemGroups = async () => {
      const res = await itemGroupApi.list({ status: 'ACTIVE', size: 1000 });
      setItemGroups(res.data.content);
    };

    const loadHsnCodes = async () => {
      const res = await hsnApi.list({ status: 'ACTIVE', size: 1000 });
      setHsnCodes(res.data.content);
    };

    const loadUnits = async () => {
      const res = await unitApi.list({ status: 'ACTIVE', size: 1000 });
      setUnits(res.data.content);
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
      setManualCode(product.manualCode || '');
      setItemGroupId(product.itemGroupId ? String(product.itemGroupId) : '');
      setHsnId(product.hsnId ? String(product.hsnId) : '');
      setPurchaseUnitId(product.purchaseUnitId ? String(product.purchaseUnitId) : '');
      setSaleUnitId(product.saleUnitId ? String(product.saleUnitId) : '');
      setTolerancePercent(product.tolerancePercent != null ? String(product.tolerancePercent) : '');
      setItemType(product.itemType || '');
      setTaxNature(product.taxNature || '');
      setTaxBasedOn(product.taxBasedOn || '');
      setPartyName(product.partyName || '');
      setPartyProductName(product.partyProductName || '');
      setFreeValue(product.freeValue != null ? String(product.freeValue) : '');
      setApplicableProperty(product.applicableProperty || '');
    };

    Promise.all([loadCategories(), loadItemGroups(), loadHsnCodes(), loadUnits(), loadProduct()]).finally(() =>
      setLoading(false)
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (!loading && initialSnapshot.current === null) {
      initialSnapshot.current = getSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

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
      manualCode: manualCode || undefined,
      itemGroupId: itemGroupId ? Number(itemGroupId) : undefined,
      hsnId: hsnId ? Number(hsnId) : undefined,
      purchaseUnitId: purchaseUnitId ? Number(purchaseUnitId) : undefined,
      saleUnitId: saleUnitId ? Number(saleUnitId) : undefined,
      tolerancePercent: tolerancePercent ? toNumber(tolerancePercent) : undefined,
      itemType: itemType || undefined,
      taxNature: taxNature || undefined,
      taxBasedOn: taxBasedOn || undefined,
      partyName: partyName || undefined,
      partyProductName: partyProductName || undefined,
      freeValue: freeValue ? toNumber(freeValue) : undefined,
      applicableProperty: applicableProperty || undefined,
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
      <BackButton label="Back to Products" onClick={() => guardedNavigate('/products')} />

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

        <Card>
          <CardHeader>
            <CardTitle>Item Master Details</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="manualCode">Manual Code</Label>
              <Input id="manualCode" value={manualCode} onChange={(e) => setManualCode(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="itemGroup">Item Group</Label>
              <Select value={itemGroupId} onValueChange={(v) => v && setItemGroupId(v)}>
                <SelectTrigger id="itemGroup">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  {itemGroups.map((g) => (
                    <SelectItem key={g.id} value={String(g.id)}>
                      {g.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="hsn">HSN Code</Label>
              <Select value={hsnId} onValueChange={(v) => v && setHsnId(v)}>
                <SelectTrigger id="hsn">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  {hsnCodes.map((h) => (
                    <SelectItem key={h.id} value={String(h.id)}>
                      {h.hsnCode}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="purchaseUnit">Purchase Unit</Label>
              <Select value={purchaseUnitId} onValueChange={(v) => v && setPurchaseUnitId(v)}>
                <SelectTrigger id="purchaseUnit">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  {units.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {u.name} ({u.symbol})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="saleUnit">Sale Unit</Label>
              <Select value={saleUnitId} onValueChange={(v) => v && setSaleUnitId(v)}>
                <SelectTrigger id="saleUnit">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  {units.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {u.name} ({u.symbol})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="tolerancePercent">Tolerance %</Label>
              <Input
                id="tolerancePercent"
                type="number"
                min={0}
                step="0.01"
                value={tolerancePercent}
                onChange={(e) => setTolerancePercent(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="itemType">Item Type</Label>
              <Input id="itemType" value={itemType} onChange={(e) => setItemType(e.target.value)} placeholder="e.g. Goods, Service" />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="taxNature">Tax Nature</Label>
              <Input id="taxNature" value={taxNature} onChange={(e) => setTaxNature(e.target.value)} placeholder="e.g. Taxable, Exempt" />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="taxBasedOn">Tax Based On</Label>
              <Input id="taxBasedOn" value={taxBasedOn} onChange={(e) => setTaxBasedOn(e.target.value)} placeholder="e.g. Value, Quantity" />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="partyName">Party Name</Label>
              <Input id="partyName" value={partyName} onChange={(e) => setPartyName(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="partyProductName">Party Product Name</Label>
              <Input id="partyProductName" value={partyProductName} onChange={(e) => setPartyProductName(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="freeValue">Free Value</Label>
              <Input
                id="freeValue"
                type="number"
                min={0}
                step="0.01"
                value={freeValue}
                onChange={(e) => setFreeValue(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="applicableProperty">Applicable Property</Label>
              <Input
                id="applicableProperty"
                value={applicableProperty}
                onChange={(e) => setApplicableProperty(e.target.value)}
              />
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            {isEdit ? 'Save Changes' : 'Create Product'}
          </Button>
          <Button type="button" variant="outline" onClick={() => guardedNavigate('/products')}>
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

      <ConfirmDialog
        open={confirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this product. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
