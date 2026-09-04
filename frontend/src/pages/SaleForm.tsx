import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import CustomerQuickAddModal from '../components/CustomerQuickAddModal';
import ProductQuickAddModal from '../components/ProductQuickAddModal';
import { customerApi } from '../api/customerApi';
import { productApi } from '../api/productApi';
import { saleApi } from '../api/saleApi';
import { parseApiError } from '../utils/apiError';
import { PaymentStatus } from '../types/purchase';
import { Product } from '../types/product';
import { Customer, Sale, SaleStatus } from '../types/sale';
import { useAuth } from '../context/AuthContext';
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
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface ItemRow {
  productId: string;
  quantity: string;
  sellingPrice: string;
  discount: string;
  tax: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', sellingPrice: '', discount: '0', tax: '0' };
const WALK_IN = '__walk_in__';

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowSubtotal(row: ItemRow): number {
  return toNumber(row.quantity) * toNumber(row.sellingPrice) - toNumber(row.discount) + toNumber(row.tax);
}

export default function SaleForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManageProducts = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Tracks quantities already committed by THIS sale before edits, so we can
  // compute the true available stock while editing an already-completed sale
  // (its own committed quantity is not yet "free" until the edit is saved).
  const [reservedQuantityByProduct, setReservedQuantityByProduct] = useState<Record<number, number>>({});

  const [customerId, setCustomerId] = useState('');
  const [saleDate, setSaleDate] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('UNPAID');
  const [status, setStatus] = useState<SaleStatus>('COMPLETED');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () => JSON.stringify({ customerId, saleDate, paymentStatus, status, notes, items });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    const loadReferenceData = async () => {
      const [customerRes, productRes] = await Promise.all([customerApi.list(), productApi.list({ size: 200 })]);
      setCustomers(customerRes.data);
      setProducts(productRes.data.content);
    };

    const loadSale = async () => {
      if (!isEdit) return;
      const res = await saleApi.getById(Number(id));
      const sale: Sale = res.data;
      setCustomerId(sale.customer ? String(sale.customer.id) : '');
      setSaleDate(sale.saleDate);
      setPaymentStatus(sale.paymentStatus);
      setStatus(sale.status);
      setNotes(sale.notes || '');
      setItems(
        sale.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          sellingPrice: String(item.sellingPrice),
          discount: String(item.discount),
          tax: String(item.tax),
        }))
      );

      if (sale.status === 'COMPLETED') {
        const reserved: Record<number, number> = {};
        sale.items.forEach((item) => {
          reserved[item.product.id] = (reserved[item.product.id] || 0) + item.quantity;
        });
        setReservedQuantityByProduct(reserved);
      }
    };

    Promise.all([loadReferenceData(), loadSale()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (!loading && initialSnapshot.current === null) {
      initialSnapshot.current = getSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  const getProduct = (productId: string) => products.find((p) => String(p.id) === productId);

  const availableStock = (productId: string): number | null => {
    const product = getProduct(productId);
    if (!product) return null;
    const reserved = reservedQuantityByProduct[product.id] || 0;
    return product.stockQuantity + reserved;
  };

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const updated = { ...row, [field]: value };
        if (field === 'productId') {
          const product = getProduct(value);
          updated.sellingPrice = product ? String(product.sellingPrice) : '';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItemRow = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const subtotalAmount = items.reduce((sum, row) => sum + toNumber(row.quantity) * toNumber(row.sellingPrice), 0);
  const totalDiscount = items.reduce((sum, row) => sum + toNumber(row.discount), 0);
  const totalTax = items.reduce((sum, row) => sum + toNumber(row.tax), 0);
  const grandTotal = items.reduce((sum, row) => sum + rowSubtotal(row), 0);

  const validate = (): string | null => {
    if (!saleDate) return 'Sale date is required';
    if (items.length === 0) return 'At least one sale item is required';

    const seenProducts = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seenProducts.has(row.productId)) {
        return 'The same product cannot be added more than once. Update the existing item instead.';
      }
      seenProducts.add(row.productId);

      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (toNumber(row.sellingPrice) < 0) return 'Selling price must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
      if (toNumber(row.tax) < 0) return 'Tax cannot be negative';

      const available = availableStock(row.productId);
      if (available !== null && toNumber(row.quantity) > available) {
        const product = getProduct(row.productId);
        return `Quantity for ${product?.name} exceeds available stock (available: ${available})`;
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

    const payload = {
      customerId: customerId ? Number(customerId) : null,
      saleDate,
      paymentStatus,
      notes,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        sellingPrice: toNumber(row.sellingPrice),
        discount: toNumber(row.discount),
        tax: toNumber(row.tax),
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await saleApi.update(Number(id), { ...payload, status });
        toast.success('Sale updated successfully');
      } else {
        await saleApi.create(payload);
        toast.success('Sale created successfully');
      }
      navigate('/sales');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save sale');
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
      <BackButton label="Back to Sales" onClick={() => guardedNavigate('/sales')} />

      <PageHeader title={isEdit ? 'Edit Sale' : 'Create Sale'} description="Record a sale to a customer or walk-in buyer." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Sale Information</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-1.5">
              <Label>Customer</Label>
              <div className="flex gap-2">
                <Select
                  value={customerId || WALK_IN}
                  onValueChange={(v) => setCustomerId(v === WALK_IN ? '' : v)}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={WALK_IN}>Walk-in / no customer</SelectItem>
                    {customers.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.firstName} {c.lastName} ({c.mobile})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" size="icon" onClick={() => setShowCustomerModal(true)}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="saleDate">Sale Date</Label>
              <Input
                id="saleDate"
                type="date"
                value={saleDate}
                onChange={(e) => setSaleDate(e.target.value)}
                invalid={!!fieldErrors.saleDate}
              />
              {fieldErrors.saleDate && <p className="text-xs text-destructive">{fieldErrors.saleDate}</p>}
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
                <Label>Sale Status</Label>
                <Select value={status} onValueChange={(v) => setStatus(v as SaleStatus)}>
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
            <CardTitle>Sale Items</CardTitle>
            <div className="flex gap-2">
              {canManageProducts && (
                <Button type="button" variant="outline" size="sm" onClick={() => setShowProductModal(true)}>
                  <Plus className="h-4 w-4" /> New Product
                </Button>
              )}
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
                    <TableHead className="min-w-[220px]">Product</TableHead>
                    <TableHead className="w-24">Qty</TableHead>
                    <TableHead className="w-32">Price</TableHead>
                    <TableHead className="w-28">Discount</TableHead>
                    <TableHead className="w-28">Tax</TableHead>
                    <TableHead className="w-28 text-right">Subtotal</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row, index) => {
                    const stock = availableStock(row.productId);
                    return (
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
                          {stock !== null && (
                            <Badge variant={stock === 0 ? 'destructive' : 'muted'} className="mt-1.5">
                              Stock: {stock}
                            </Badge>
                          )}
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
                            value={row.sellingPrice}
                            onChange={(e) => updateItem(index, 'sellingPrice', e.target.value)}
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
                    );
                  })}
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
            {isEdit ? 'Save Changes' : 'Create Sale'}
          </Button>
          <Button type="button" variant="outline" onClick={() => guardedNavigate('/sales')}>
            Cancel
          </Button>
        </div>
      </form>

      <CustomerQuickAddModal
        show={showCustomerModal}
        onClose={() => setShowCustomerModal(false)}
        onCreated={(customer) => {
          setCustomers((prev) => [...prev, customer]);
          setCustomerId(String(customer.id));
          setShowCustomerModal(false);
          toast.success('Customer added');
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
        description="You have unsaved changes to this sale. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
