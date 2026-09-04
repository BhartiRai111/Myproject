import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import SupplierQuickAddModal from '../components/SupplierQuickAddModal';
import ProductQuickAddModal from '../components/ProductQuickAddModal';
import { productApi } from '../api/productApi';
import { purchaseApi } from '../api/purchaseApi';
import { supplierApi } from '../api/supplierApi';
import { parseApiError } from '../utils/apiError';
import { PaymentStatus, Purchase, PurchaseStatus } from '../types/purchase';
import { Product } from '../types/product';
import { Supplier } from '../types/supplier';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface ItemRow {
  productId: string;
  quantity: string;
  purchasePrice: string;
  discount: string;
  tax: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', purchasePrice: '', discount: '0', tax: '0' };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowSubtotal(row: ItemRow): number {
  return toNumber(row.quantity) * toNumber(row.purchasePrice) - toNumber(row.discount) + toNumber(row.tax);
}

export default function PurchaseForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [supplierId, setSupplierId] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('UNPAID');
  const [status, setStatus] = useState<PurchaseStatus>('PENDING');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  useEffect(() => {
    const loadReferenceData = async () => {
      const [supplierRes, productRes] = await Promise.all([
        supplierApi.list({ size: 200 }),
        productApi.list({ size: 200 }),
      ]);
      setSuppliers(supplierRes.data.content);
      setProducts(productRes.data.content);
    };

    const loadPurchase = async () => {
      if (!isEdit) return;
      const res = await purchaseApi.getById(Number(id));
      const purchase: Purchase = res.data;
      setSupplierId(String(purchase.supplier.id));
      setPurchaseDate(purchase.purchaseDate);
      setPaymentStatus(purchase.paymentStatus);
      setStatus(purchase.status);
      setNotes(purchase.notes || '');
      setItems(
        purchase.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          purchasePrice: String(item.purchasePrice),
          discount: String(item.discount),
          tax: String(item.tax),
        }))
      );
    };

    Promise.all([loadReferenceData(), loadPurchase()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const getProduct = (productId: string) => products.find((p) => String(p.id) === productId);

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const updated = { ...row, [field]: value };
        if (field === 'productId') {
          const product = getProduct(value);
          updated.purchasePrice = product ? String(product.purchasePrice) : '';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItemRow = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const subtotalAmount = items.reduce((sum, row) => sum + toNumber(row.quantity) * toNumber(row.purchasePrice), 0);
  const totalDiscount = items.reduce((sum, row) => sum + toNumber(row.discount), 0);
  const totalTax = items.reduce((sum, row) => sum + toNumber(row.tax), 0);
  const grandTotal = items.reduce((sum, row) => sum + rowSubtotal(row), 0);

  const validate = (): string | null => {
    if (!supplierId) return 'Supplier is required';
    if (!purchaseDate) return 'Purchase date is required';
    if (items.length === 0) return 'At least one purchase item is required';

    const seenProducts = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seenProducts.has(row.productId)) {
        return 'The same product cannot be added more than once. Update the existing item instead.';
      }
      seenProducts.add(row.productId);

      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (toNumber(row.purchasePrice) < 0) return 'Purchase price must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
      if (toNumber(row.tax) < 0) return 'Tax cannot be negative';
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

    const payload = {
      supplierId: Number(supplierId),
      purchaseDate,
      paymentStatus,
      notes,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        purchasePrice: toNumber(row.purchasePrice),
        discount: toNumber(row.discount),
        tax: toNumber(row.tax),
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await purchaseApi.update(Number(id), { ...payload, status });
        toast.success('Purchase updated successfully');
      } else {
        await purchaseApi.create(payload);
        toast.success('Purchase created successfully');
      }
      navigate('/purchases');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save purchase');
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
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader title={isEdit ? 'Edit Purchase' : 'Create Purchase'} description="Record stock ordered from a supplier." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Purchase Information</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-1.5">
              <Label>Supplier</Label>
              <div className="flex gap-2">
                <Select value={supplierId} onValueChange={setSupplierId}>
                  <SelectTrigger className={fieldErrors.supplierId ? 'border-destructive' : ''}>
                    <SelectValue placeholder="Select supplier" />
                  </SelectTrigger>
                  <SelectContent>
                    {suppliers.map((s) => (
                      <SelectItem key={s.id} value={String(s.id)} disabled={s.status === 'INACTIVE'}>
                        {s.name}
                        {s.status === 'INACTIVE' ? ' (Inactive)' : ''}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" size="icon" onClick={() => setShowSupplierModal(true)}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
              {fieldErrors.supplierId && <p className="text-xs text-destructive">{fieldErrors.supplierId}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="purchaseDate">Purchase Date</Label>
              <Input
                id="purchaseDate"
                type="date"
                value={purchaseDate}
                onChange={(e) => setPurchaseDate(e.target.value)}
                invalid={!!fieldErrors.purchaseDate}
              />
              {fieldErrors.purchaseDate && <p className="text-xs text-destructive">{fieldErrors.purchaseDate}</p>}
            </div>

            <div className="space-y-1.5">
              <Label>Payment Status</Label>
              <Select value={paymentStatus} onValueChange={(v) => setPaymentStatus(v as PaymentStatus)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNPAID">Unpaid</SelectItem>
                  <SelectItem value="PARTIAL">Partial</SelectItem>
                  <SelectItem value="PAID">Paid</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {isEdit && (
              <div className="space-y-1.5">
                <Label>Purchase Status</Label>
                <Select value={status} onValueChange={(v) => setStatus(v as PurchaseStatus)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PENDING">Pending</SelectItem>
                    <SelectItem value="COMPLETED">Completed</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="space-y-1.5 sm:col-span-2 lg:col-span-4">
              <Label htmlFor="notes">Notes</Label>
              <Textarea id="notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Purchase Items</CardTitle>
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setShowProductModal(true)}>
                <Plus className="h-4 w-4" /> New Product
              </Button>
              <Button type="button" size="sm" onClick={addItemRow}>
                <Plus className="h-4 w-4" /> Add Item
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="overflow-hidden rounded-lg border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="min-w-[200px]">Product</TableHead>
                    <TableHead className="w-24">Qty</TableHead>
                    <TableHead className="w-32">Price</TableHead>
                    <TableHead className="w-28">Discount</TableHead>
                    <TableHead className="w-28">Tax</TableHead>
                    <TableHead className="w-28 text-right">Subtotal</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row, index) => (
                    <TableRow key={index}>
                      <TableCell>
                        <Select value={row.productId} onValueChange={(v) => updateItem(index, 'productId', v)}>
                          <SelectTrigger>
                            <SelectValue placeholder="Select product" />
                          </SelectTrigger>
                          <SelectContent>
                            {products.map((p) => (
                              <SelectItem key={p.id} value={String(p.id)} disabled={p.status === 'INACTIVE'}>
                                {p.name} ({p.unit}){p.status === 'INACTIVE' ? ' (Inactive)' : ''}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={1}
                          value={row.quantity}
                          onChange={(e) => updateItem(index, 'quantity', e.target.value)}
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          step="0.01"
                          value={row.purchasePrice}
                          onChange={(e) => updateItem(index, 'purchasePrice', e.target.value)}
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          step="0.01"
                          value={row.discount}
                          onChange={(e) => updateItem(index, 'discount', e.target.value)}
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          step="0.01"
                          value={row.tax}
                          onChange={(e) => updateItem(index, 'tax', e.target.value)}
                        />
                      </TableCell>
                      <TableCell className="text-right font-medium">{rowSubtotal(row).toFixed(2)}</TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive hover:text-destructive"
                          onClick={() => removeItemRow(index)}
                          disabled={items.length === 1}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="mt-4 flex justify-end">
              <div className="w-full max-w-xs space-y-1.5 text-sm">
                <div className="flex justify-between text-muted-foreground">
                  <span>Subtotal</span>
                  <span>{subtotalAmount.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>Discount</span>
                  <span>-{totalDiscount.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>Tax</span>
                  <span>+{totalTax.toFixed(2)}</span>
                </div>
                <Separator />
                <div className="flex justify-between text-base font-semibold">
                  <span>Grand Total</span>
                  <span>{grandTotal.toFixed(2)}</span>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            {isEdit ? 'Save Changes' : 'Create Purchase'}
          </Button>
          <Button type="button" variant="outline" onClick={() => navigate('/purchases')}>
            Cancel
          </Button>
        </div>
      </form>

      <SupplierQuickAddModal
        show={showSupplierModal}
        onClose={() => setShowSupplierModal(false)}
        onCreated={(supplier) => {
          setSuppliers((prev) => [...prev, supplier]);
          setShowSupplierModal(false);
          setTimeout(() => setSupplierId(String(supplier.id)), 0);
          toast.success('Supplier added');
        }}
      />

      <ProductQuickAddModal
        show={showProductModal}
        onClose={() => setShowProductModal(false)}
        onCreated={(product) => {
          setProducts((prev) => [...prev, product]);
          setShowProductModal(false);
          toast.success('Product added');
        }}
      />
    </div>
  );
}
