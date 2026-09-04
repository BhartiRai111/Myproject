import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import SupplierQuickAddModal from '../../components/SupplierQuickAddModal';
import ProductQuickAddModal from '../../components/ProductQuickAddModal';
import { productApi } from '../../api/productApi';
import { supplierApi } from '../../api/supplierApi';
import { purchaseOrderApi } from '../../api/purchaseOrderApi';
import { parseApiError } from '../../utils/apiError';
import { Product } from '../../types/product';
import { Supplier } from '../../types/purchase';
import { PurchaseOrder, PurchaseOrderPayload } from '../../types/purchaseOrder';
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
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface ItemRow {
  productId: string;
  quantity: string;
  rate: string;
  discount: string;
  gstPercent: string;
  minQuantity: number;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', rate: '', discount: '0', gstPercent: '0', minQuantity: 0 };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowTotal(row: ItemRow): number {
  const taxable = toNumber(row.quantity) * toNumber(row.rate) - toNumber(row.discount);
  const gst = taxable * (toNumber(row.gstPercent) / 100);
  return taxable + gst;
}

export default function PurchaseOrderForm() {
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
  const [supplierPhone, setSupplierPhone] = useState('');
  const [supplierGstin, setSupplierGstin] = useState('');
  const [billingAddress, setBillingAddress] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [orderDate, setOrderDate] = useState('');
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState('');
  const [remarks, setRemarks] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () =>
    JSON.stringify({ supplierId, supplierPhone, supplierGstin, billingAddress, shippingAddress, orderDate, expectedDeliveryDate, remarks, items });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    const loadReferenceData = async () => {
      const [supplierRes, productRes] = await Promise.all([
        supplierApi.list({ size: 200 }),
        productApi.list({ size: 200, status: 'ACTIVE' }),
      ]);
      setSuppliers(supplierRes.data.content);
      setProducts(productRes.data.content);
    };

    const loadOrder = async () => {
      if (!isEdit) return;
      const res = await purchaseOrderApi.getById(Number(id));
      const order: PurchaseOrder = res.data;
      setSupplierId(order.supplier ? String(order.supplier.id) : '');
      setSupplierPhone(order.supplierPhone || '');
      setSupplierGstin(order.supplierGstin || '');
      setBillingAddress(order.billingAddress || '');
      setShippingAddress(order.shippingAddress || '');
      setOrderDate(order.orderDate);
      setExpectedDeliveryDate(order.expectedDeliveryDate || '');
      setRemarks(order.remarks || '');
      setItems(
        order.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          rate: String(item.rate),
          discount: String(item.discount),
          gstPercent: String(item.gstPercent),
          minQuantity: item.receivedQuantity,
        }))
      );
    };

    Promise.all([loadReferenceData(), loadOrder()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (!loading && initialSnapshot.current === null) {
      initialSnapshot.current = getSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  const getProduct = (productId: string) => products.find((p) => String(p.id) === productId);

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const updated = { ...row, [field]: value };
        if (field === 'productId') {
          const product = getProduct(value);
          updated.rate = product ? String(product.purchasePrice) : '';
          updated.gstPercent = product ? String(product.tax) : '0';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItemRow = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const grandTotal = items.reduce((sum, row) => sum + rowTotal(row), 0);

  const validate = (): string | null => {
    if (!supplierId) return 'Supplier is required';
    if (items.length === 0) return 'At least one order item is required';
    const seen = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seen.has(row.productId)) return 'The same product cannot be added more than once. Update the existing item instead.';
      seen.add(row.productId);
      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (row.minQuantity > 0 && toNumber(row.quantity) < row.minQuantity) {
        return `Quantity cannot be reduced below ${row.minQuantity} (already received)`;
      }
      if (toNumber(row.rate) < 0) return 'Purchase rate must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
      if (toNumber(row.gstPercent) < 0) return 'GST % cannot be negative';
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

    const payload: PurchaseOrderPayload = {
      supplierId: Number(supplierId),
      supplierPhone: supplierPhone || undefined,
      supplierGstin: supplierGstin || undefined,
      billingAddress: billingAddress || undefined,
      shippingAddress: shippingAddress || undefined,
      orderDate,
      expectedDeliveryDate: expectedDeliveryDate || undefined,
      remarks: remarks || undefined,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        rate: toNumber(row.rate),
        discount: toNumber(row.discount),
        gstPercent: toNumber(row.gstPercent),
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        const res = await purchaseOrderApi.update(Number(id), payload);
        toast.success('Purchase order updated successfully');
        navigate(`/purchases/orders/${res.data.id}`);
      } else {
        const res = await purchaseOrderApi.create(payload);
        toast.success('Purchase order created successfully');
        navigate(`/purchases/orders/${res.data.id}`);
      }
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save purchase order');
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
      <BackButton label="Back to Purchase Orders" onClick={() => guardedNavigate('/purchases/orders')} />

      <PageHeader
        title={isEdit ? 'Edit Purchase Order' : 'New Purchase Order'}
        description="Purchase orders do not affect stock or accounts until billed."
      />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Supplier &amp; Order Details</CardTitle>
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
                      <SelectItem key={s.id} value={String(s.id)}>
                        {s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" size="icon" onClick={() => setShowSupplierModal(true)}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="supplierPhone">Supplier Phone</Label>
              <Input id="supplierPhone" value={supplierPhone} onChange={(e) => setSupplierPhone(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="supplierGstin">Supplier GSTIN</Label>
              <Input id="supplierGstin" value={supplierGstin} onChange={(e) => setSupplierGstin(e.target.value)} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="orderDate">Order Date</Label>
              <Input
                id="orderDate"
                type="date"
                value={orderDate}
                onChange={(e) => setOrderDate(e.target.value)}
                invalid={!!fieldErrors.orderDate}
              />
              {fieldErrors.orderDate && <p className="text-xs text-destructive">{fieldErrors.orderDate}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="expectedDeliveryDate">Expected Delivery Date</Label>
              <Input
                id="expectedDeliveryDate"
                type="date"
                value={expectedDeliveryDate}
                onChange={(e) => setExpectedDeliveryDate(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="billingAddress">Billing Address</Label>
              <Textarea id="billingAddress" rows={2} value={billingAddress} onChange={(e) => setBillingAddress(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="shippingAddress">Shipping Address</Label>
              <Textarea id="shippingAddress" rows={2} value={shippingAddress} onChange={(e) => setShippingAddress(e.target.value)} />
            </div>

            <div className="space-y-1.5 sm:col-span-2 lg:col-span-4">
              <Label htmlFor="remarks">Remarks</Label>
              <Textarea id="remarks" rows={2} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Order Items</CardTitle>
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
                    <TableHead className="w-28">Rate</TableHead>
                    <TableHead className="w-24">Discount</TableHead>
                    <TableHead className="w-20">GST %</TableHead>
                    <TableHead className="w-28 text-right">Total</TableHead>
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
                              <SelectItem key={p.id} value={String(p.id)}>
                                {p.name} ({p.unit})
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {row.minQuantity > 0 && (
                          <p className="mt-1 text-xs text-muted-foreground">Already received: {row.minQuantity}</p>
                        )}
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={row.minQuantity || 1}
                          value={row.quantity}
                          onChange={(e) => updateItem(index, 'quantity', e.target.value)}
                        />
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} step="0.01" value={row.rate} onChange={(e) => updateItem(index, 'rate', e.target.value)} />
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} step="0.01" value={row.discount} onChange={(e) => updateItem(index, 'discount', e.target.value)} />
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} step="0.01" value={row.gstPercent} onChange={(e) => updateItem(index, 'gstPercent', e.target.value)} />
                      </TableCell>
                      <TableCell className="text-right font-medium">{rowTotal(row).toFixed(2)}</TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive hover:text-destructive"
                          onClick={() => removeItemRow(index)}
                          disabled={items.length === 1 || row.minQuantity > 0}
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
            {isEdit ? 'Save Changes' : 'Create Order'}
          </Button>
          <Button type="button" variant="outline" onClick={() => guardedNavigate('/purchases/orders')}>
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

      <ConfirmDialog
        open={confirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this purchase order. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
