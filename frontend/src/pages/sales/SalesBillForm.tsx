import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import CustomerQuickAddModal from '../../components/CustomerQuickAddModal';
import ProductQuickAddModal from '../../components/ProductQuickAddModal';
import { productApi } from '../../api/productApi';
import { customerApi } from '../../api/customerApi';
import { saleApi } from '../../api/saleApi';
import { salesOrderApi } from '../../api/salesOrderApi';
import { parseApiError } from '../../utils/apiError';
import { Product } from '../../types/product';
import { Customer, GstType, PaymentMode, Sale, SaleCreatePayload, TaxMode } from '../../types/sale';
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
  sellingPrice: string;
  discount: string;
  gstPercent: string;
  salesOrderItemId?: number;
  maxQuantity?: number;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', sellingPrice: '', discount: '0', gstPercent: '0' };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowCalc(row: ItemRow, isGst: boolean) {
  const taxable = toNumber(row.quantity) * toNumber(row.sellingPrice) - toNumber(row.discount);
  const gst = isGst ? taxable * (toNumber(row.gstPercent) / 100) : 0;
  return { taxable, gst, total: taxable + gst };
}

export default function SalesBillForm() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId');
  const isEdit = !!id;
  const navigate = useNavigate();

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(isEdit || !!orderId);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [existingSale, setExistingSale] = useState<Sale | null>(null);

  const [customerId, setCustomerId] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerGstin, setCustomerGstin] = useState('');
  const [billingAddress, setBillingAddress] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [saleDate, setSaleDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [gstType, setGstType] = useState<GstType>('NON_GST');
  const [taxMode, setTaxMode] = useState<TaxMode>('INTRA_STATE');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [paidAmount, setPaidAmount] = useState('0');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);
  const [salesOrderId, setSalesOrderId] = useState<number | undefined>(orderId ? Number(orderId) : undefined);
  const [salesOrderNumber, setSalesOrderNumber] = useState<string | null>(null);

  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  const isGst = gstType === 'GST';

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () =>
    JSON.stringify({ customerId, customerPhone, customerGstin, billingAddress, shippingAddress, saleDate, gstType, taxMode, paymentMode, paidAmount, notes, items });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    const loadReferenceData = async () => {
      const [customerRes, productRes] = await Promise.all([customerApi.list(), productApi.list({ size: 200, status: 'ACTIVE' })]);
      setCustomers(customerRes.data);
      setProducts(productRes.data.content);
    };

    const loadSale = async () => {
      if (!isEdit) return;
      const res = await saleApi.getById(Number(id));
      const sale = res.data;
      setExistingSale(sale);
      setCustomerId(sale.customer ? String(sale.customer.id) : '');
      setCustomerPhone(sale.customerPhone || '');
      setCustomerGstin(sale.customerGstin || '');
      setBillingAddress(sale.billingAddress || '');
      setShippingAddress(sale.shippingAddress || '');
      setSaleDate(sale.saleDate);
      setGstType(sale.gstType);
      setTaxMode(sale.taxMode || 'INTRA_STATE');
      setPaymentMode(sale.paymentMode);
      setPaidAmount(String(sale.paidAmount));
      setNotes(sale.notes || '');
      setSalesOrderId(sale.salesOrderId || undefined);
      setSalesOrderNumber(sale.salesOrderNumber || null);
      setItems(
        sale.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          sellingPrice: String(item.sellingPrice),
          discount: String(item.discount),
          gstPercent: String(item.gstPercent || 0),
          salesOrderItemId: item.salesOrderItemId || undefined,
        }))
      );
    };

    const loadFromOrder = async () => {
      if (isEdit || !orderId) return;
      const res = await salesOrderApi.getById(Number(orderId));
      const order = res.data;
      setCustomerId(order.customer ? String(order.customer.id) : '');
      setCustomerPhone(order.customerPhone || '');
      setCustomerGstin(order.customerGstin || '');
      setBillingAddress(order.billingAddress || '');
      setShippingAddress(order.shippingAddress || '');
      setSalesOrderId(order.id);
      setSalesOrderNumber(order.orderNumber);
      const anyGst = order.items.some((i) => i.gstPercent > 0);
      setGstType(anyGst ? 'GST' : 'NON_GST');
      const billable = order.items.filter((i) => i.remainingQuantity > 0);
      if (billable.length > 0) {
        setItems(
          billable.map((item) => ({
            productId: String(item.product.id),
            quantity: String(item.remainingQuantity),
            sellingPrice: String(item.rate),
            discount: String(item.discount),
            gstPercent: String(item.gstPercent),
            salesOrderItemId: item.id,
            maxQuantity: item.remainingQuantity,
          }))
        );
      }
    };

    Promise.all([loadReferenceData(), loadSale(), loadFromOrder()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, orderId]);

  useEffect(() => {
    if (!loading && initialSnapshot.current === null) {
      initialSnapshot.current = getSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  const locked = isEdit && !!existingSale?.hasReceipts;

  const getProduct = (productId: string) => products.find((p) => String(p.id) === productId);

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const updated = { ...row, [field]: value };
        if (field === 'productId') {
          const product = getProduct(value);
          updated.sellingPrice = product ? String(product.sellingPrice) : '';
          updated.gstPercent = product ? String(product.tax) : '0';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItemRow = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const taxableTotal = items.reduce((sum, row) => sum + rowCalc(row, isGst).taxable, 0);
  const gstTotal = items.reduce((sum, row) => sum + rowCalc(row, isGst).gst, 0);
  const cgstTotal = isGst && taxMode === 'INTRA_STATE' ? gstTotal / 2 : 0;
  const sgstTotal = isGst && taxMode === 'INTRA_STATE' ? gstTotal / 2 : 0;
  const igstTotal = isGst && taxMode === 'INTER_STATE' ? gstTotal : 0;
  const grandTotal = items.reduce((sum, row) => sum + rowCalc(row, isGst).total, 0);
  const dueAmount = Math.max(0, grandTotal - toNumber(paidAmount));

  const validate = (): string | null => {
    if (items.length === 0) return 'At least one item is required';
    if (isGst && !taxMode) return 'Tax mode is required for a GST sale';
    const seen = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seen.has(row.productId)) return 'The same product cannot be added more than once. Update the existing item instead.';
      seen.add(row.productId);
      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (row.maxQuantity !== undefined && toNumber(row.quantity) > row.maxQuantity) {
        return `Quantity cannot exceed the remaining order quantity of ${row.maxQuantity}`;
      }
      if (toNumber(row.sellingPrice) < 0) return 'Rate must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
    }
    if (toNumber(paidAmount) < 0) return 'Paid amount cannot be negative';
    if (toNumber(paidAmount) > grandTotal) return 'Paid amount cannot exceed the grand total';
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

    const payload: SaleCreatePayload = {
      customerId: customerId ? Number(customerId) : null,
      saleDate,
      gstType,
      taxMode: isGst ? taxMode : undefined,
      customerPhone: customerPhone || undefined,
      customerGstin: customerGstin || undefined,
      billingAddress: billingAddress || undefined,
      shippingAddress: shippingAddress || undefined,
      paymentMode,
      paidAmount: toNumber(paidAmount),
      notes: notes || undefined,
      salesOrderId,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        sellingPrice: toNumber(row.sellingPrice),
        discount: toNumber(row.discount),
        tax: rowCalc(row, isGst).gst,
        gstPercent: isGst ? toNumber(row.gstPercent) : 0,
        salesOrderItemId: row.salesOrderItemId,
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        const res = await saleApi.update(Number(id), { ...payload, status: existingSale?.status || 'COMPLETED' });
        toast.success('Sales bill updated successfully');
        navigate(`/sales/bills/${res.data.id}`);
      } else {
        const res = await saleApi.create(payload);
        toast.success('Sales bill created successfully');
        navigate(`/sales/bills/${res.data.id}`);
      }
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save sales bill');
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
      <BackButton label="Back to Sales Bills" onClick={() => guardedNavigate('/sales/bills')} />

      <PageHeader
        title={isEdit ? 'Edit Sales Bill' : 'New Sales Bill'}
        description={
          salesOrderNumber
            ? `Billing against Sales Order ${salesOrderNumber}`
            : 'Create a GST or Non-GST invoice for a customer.'
        }
      />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {locked && (
        <Alert>
          <AlertDescription>
            This bill already has a receipt recorded against it, so its items and amounts cannot be edited. Delete the
            receipt first, or use Receipt Entry to record further payments.
          </AlertDescription>
        </Alert>
      )}

      <fieldset disabled={locked} className="space-y-6">
        <form onSubmit={handleSubmit} className="space-y-6" noValidate>
          <Card>
            <CardHeader>
              <CardTitle>Sale Type</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label>Sale Type</Label>
                <Select value={gstType} onValueChange={(v) => setGstType(v as GstType)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NON_GST">Non-GST Sale</SelectItem>
                    <SelectItem value="GST">GST Sale</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {isGst && (
                <div className="space-y-1.5">
                  <Label>Tax Mode</Label>
                  <Select value={taxMode} onValueChange={(v) => setTaxMode(v as TaxMode)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INTRA_STATE">Intra-State (CGST + SGST)</SelectItem>
                      <SelectItem value="INTER_STATE">Inter-State (IGST)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Customer &amp; Invoice Details</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="space-y-1.5">
                <Label>Customer</Label>
                <div className="flex gap-2">
                  <Select value={customerId} onValueChange={setCustomerId}>
                    <SelectTrigger className={fieldErrors.customerId ? 'border-destructive' : ''}>
                      <SelectValue placeholder="Walk-in / select customer" />
                    </SelectTrigger>
                    <SelectContent>
                      {customers.map((c) => (
                        <SelectItem key={c.id} value={String(c.id)}>
                          {c.firstName} {c.lastName || ''}
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
                <Label htmlFor="customerPhone">Customer Phone</Label>
                <Input id="customerPhone" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="customerGstin">Customer GSTIN</Label>
                <Input id="customerGstin" value={customerGstin} onChange={(e) => setCustomerGstin(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="saleDate">Invoice Date</Label>
                <Input
                  id="saleDate"
                  type="date"
                  value={saleDate}
                  onChange={(e) => setSaleDate(e.target.value)}
                  invalid={!!fieldErrors.saleDate}
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
              <div className="space-y-1.5 sm:col-span-2 lg:col-span-2">
                <Label htmlFor="notes">Remarks</Label>
                <Textarea id="notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <CardTitle>Bill Items</CardTitle>
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
              <div className="overflow-x-auto rounded-lg border border-border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="min-w-[180px]">Product</TableHead>
                      <TableHead className="w-20">Qty</TableHead>
                      <TableHead className="w-28">Rate</TableHead>
                      <TableHead className="w-24">Discount</TableHead>
                      {isGst && <TableHead className="w-20">GST %</TableHead>}
                      <TableHead className="w-28 text-right">Taxable</TableHead>
                      {isGst && <TableHead className="w-24 text-right">GST Amt</TableHead>}
                      <TableHead className="w-28 text-right">Total</TableHead>
                      <TableHead className="w-10" />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((row, index) => {
                      const calc = rowCalc(row, isGst);
                      return (
                        <TableRow key={index}>
                          <TableCell>
                            <Select
                              value={row.productId}
                              onValueChange={(v) => updateItem(index, 'productId', v)}
                              disabled={!!row.salesOrderItemId}
                            >
                              <SelectTrigger>
                                <SelectValue placeholder="Select product" />
                              </SelectTrigger>
                              <SelectContent>
                                {products.map((p) => (
                                  <SelectItem key={p.id} value={String(p.id)} disabled={p.status === 'INACTIVE'}>
                                    {p.name} ({p.unit})
                                    {p.status === 'INACTIVE' ? ' (Inactive)' : ''}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            {row.maxQuantity !== undefined && (
                              <p className="mt-1 text-xs text-muted-foreground">Order remaining: {row.maxQuantity}</p>
                            )}
                          </TableCell>
                          <TableCell>
                            <Input
                              type="number"
                              min={1}
                              max={row.maxQuantity}
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
                          {isGst && (
                            <TableCell>
                              <Input
                                type="number"
                                min={0}
                                step="0.01"
                                value={row.gstPercent}
                                onChange={(e) => updateItem(index, 'gstPercent', e.target.value)}
                              />
                            </TableCell>
                          )}
                          <TableCell className="text-right">{calc.taxable.toFixed(2)}</TableCell>
                          {isGst && <TableCell className="text-right">{calc.gst.toFixed(2)}</TableCell>}
                          <TableCell className="text-right font-medium">{calc.total.toFixed(2)}</TableCell>
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
                    <span>Taxable Amount</span>
                    <span>{taxableTotal.toFixed(2)}</span>
                  </div>
                  {isGst && taxMode === 'INTRA_STATE' && (
                    <>
                      <div className="flex justify-between text-muted-foreground">
                        <span>CGST</span>
                        <span>{cgstTotal.toFixed(2)}</span>
                      </div>
                      <div className="flex justify-between text-muted-foreground">
                        <span>SGST</span>
                        <span>{sgstTotal.toFixed(2)}</span>
                      </div>
                    </>
                  )}
                  {isGst && taxMode === 'INTER_STATE' && (
                    <div className="flex justify-between text-muted-foreground">
                      <span>IGST</span>
                      <span>{igstTotal.toFixed(2)}</span>
                    </div>
                  )}
                  <Separator />
                  <div className="flex justify-between text-base font-semibold">
                    <span>Grand Total</span>
                    <span>{grandTotal.toFixed(2)}</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Payment</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label>Payment Mode</Label>
                <Select value={paymentMode} onValueChange={(v) => setPaymentMode(v as PaymentMode)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CASH">Cash</SelectItem>
                    <SelectItem value="BANK">Bank</SelectItem>
                    <SelectItem value="UPI">UPI</SelectItem>
                    <SelectItem value="CARD">Card</SelectItem>
                    <SelectItem value="OTHER">Other</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="paidAmount">Paid Amount</Label>
                <Input
                  id="paidAmount"
                  type="number"
                  min={0}
                  max={grandTotal}
                  step="0.01"
                  value={paidAmount}
                  onChange={(e) => setPaidAmount(e.target.value)}
                  invalid={!!fieldErrors.paidAmount}
                />
                {fieldErrors.paidAmount && <p className="text-xs text-destructive">{fieldErrors.paidAmount}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Due Amount</Label>
                <p className="flex h-9 items-center rounded-md border border-border bg-muted px-3 text-sm font-medium">
                  {dueAmount.toFixed(2)}
                </p>
              </div>
            </CardContent>
          </Card>

          <div className="flex gap-2">
            <Button type="submit" loading={submitting}>
              {isEdit ? 'Save Changes' : 'Create Bill'}
            </Button>
            <Button type="button" variant="outline" onClick={() => guardedNavigate('/sales/bills')}>
              Cancel
            </Button>
          </div>
        </form>
      </fieldset>

      <CustomerQuickAddModal
        show={showCustomerModal}
        onClose={() => setShowCustomerModal(false)}
        onCreated={(customer) => {
          setCustomers((prev) => [...prev, customer]);
          setShowCustomerModal(false);
          setTimeout(() => setCustomerId(String(customer.id)), 0);
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
        description="You have unsaved changes to this sales bill. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
