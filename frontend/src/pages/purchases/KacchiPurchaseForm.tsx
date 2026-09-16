import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import SupplierQuickAddModal from '../../components/SupplierQuickAddModal';
import ProductQuickAddModal from '../../components/ProductQuickAddModal';
import { productApi } from '../../api/productApi';
import { supplierApi } from '../../api/supplierApi';
import { purchaseApi } from '../../api/purchaseApi';
import { parseApiError } from '../../utils/apiError';
import { Product } from '../../types/product';
import { PaymentMode, PurchaseCreatePayload, Supplier, TaxMode } from '../../types/purchase';
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
  purchasePrice: string;
  discount: string;
  gstPercent: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', purchasePrice: '', discount: '0', gstPercent: '0' };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowCalc(row: ItemRow) {
  const taxable = toNumber(row.quantity) * toNumber(row.purchasePrice) - toNumber(row.discount);
  const gst = taxable * (toNumber(row.gstPercent) / 100);
  return { taxable, gst, total: taxable + gst };
}

export default function KacchiPurchaseForm() {
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
  const [purchaseDate, setPurchaseDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [taxMode, setTaxMode] = useState<TaxMode>('INTRA_STATE');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [paidAmount, setPaidAmount] = useState('0');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);
  const [confirmPostOpen, setConfirmPostOpen] = useState(false);
  const [pendingAction, setPendingAction] = useState<'draft' | 'post' | null>(null);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () =>
    JSON.stringify({ supplierId, supplierPhone, supplierGstin, billingAddress, shippingAddress, purchaseDate, taxMode, paymentMode, paidAmount, notes, items });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    const loadReferenceData = async () => {
      const [supplierRes, productRes] = await Promise.all([supplierApi.list({ size: 500 }), productApi.list({ size: 200, status: 'ACTIVE' })]);
      setSuppliers(supplierRes.data.content);
      setProducts(productRes.data.content);
    };

    const loadPurchase = async () => {
      if (!isEdit) return;
      const res = await purchaseApi.getById(Number(id));
      const purchase = res.data;
      if (purchase.status !== 'DRAFT') {
        toast.error('Only a draft Kacchi Purchase can be edited');
        navigate(`/purchases/kacchi/${purchase.id}`);
        return;
      }
      setSupplierId(String(purchase.supplier.id));
      setSupplierPhone(purchase.supplierPhone || '');
      setSupplierGstin(purchase.supplierGstin || '');
      setBillingAddress(purchase.billingAddress || '');
      setShippingAddress(purchase.shippingAddress || '');
      setPurchaseDate(purchase.purchaseDate);
      setTaxMode(purchase.taxMode || 'INTRA_STATE');
      setPaymentMode(purchase.paymentMode);
      setPaidAmount(String(purchase.paidAmount));
      setNotes(purchase.notes || '');
      setItems(
        purchase.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          purchasePrice: String(item.purchasePrice),
          discount: String(item.discount),
          gstPercent: String(item.gstPercent || 0),
        }))
      );
    };

    Promise.all([loadReferenceData(), loadPurchase()]).finally(() => setLoading(false));
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
          updated.purchasePrice = product ? String(product.purchasePrice) : '';
          updated.gstPercent = product ? String(product.tax) : '0';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeItemRow = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index));

  const taxableTotal = items.reduce((sum, row) => sum + rowCalc(row).taxable, 0);
  const gstTotal = items.reduce((sum, row) => sum + rowCalc(row).gst, 0);
  const cgstTotal = taxMode === 'INTRA_STATE' ? gstTotal / 2 : 0;
  const sgstTotal = taxMode === 'INTRA_STATE' ? gstTotal / 2 : 0;
  const igstTotal = taxMode === 'INTER_STATE' ? gstTotal : 0;
  const grandTotal = items.reduce((sum, row) => sum + rowCalc(row).total, 0);
  const dueAmount = Math.max(0, grandTotal - toNumber(paidAmount));

  const validate = (): string | null => {
    if (!supplierId) return 'Supplier is required';
    if (items.length === 0) return 'At least one item is required';
    const seen = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seen.has(row.productId)) return 'The same product cannot be added more than once. Update the existing item instead.';
      seen.add(row.productId);
      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (toNumber(row.purchasePrice) < 0) return 'Rate must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
    }
    if (toNumber(paidAmount) < 0) return 'Paid amount cannot be negative';
    if (toNumber(paidAmount) > grandTotal) return 'Paid amount cannot exceed the grand total';
    return null;
  };

  const buildPayload = (saveAsDraft: boolean): PurchaseCreatePayload => ({
    supplierId: Number(supplierId),
    purchaseDate,
    gstType: 'GST',
    taxMode,
    supplierPhone: supplierPhone || undefined,
    supplierGstin: supplierGstin || undefined,
    billingAddress: billingAddress || undefined,
    shippingAddress: shippingAddress || undefined,
    paymentMode,
    paidAmount: toNumber(paidAmount),
    notes: notes || undefined,
    items: items.map((row) => ({
      productId: Number(row.productId),
      quantity: toNumber(row.quantity),
      purchasePrice: toNumber(row.purchasePrice),
      discount: toNumber(row.discount),
      tax: rowCalc(row).gst,
      gstPercent: toNumber(row.gstPercent),
    })),
    transactionType: 'PURCHASE_CHALLAN',
    saveAsDraft,
  });

  const runSubmit = async (action: 'draft' | 'post') => {
    setError('');
    setFieldErrors({});
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    try {
      if (isEdit) {
        await purchaseApi.update(Number(id), { ...buildPayload(true), status: 'DRAFT' });
        if (action === 'post') {
          const posted = await purchaseApi.post(Number(id));
          toast.success('Kacchi Purchase posted successfully');
          navigate(`/purchases/kacchi/${posted.data.id}`);
        } else {
          toast.success('Draft saved');
          navigate(`/purchases/kacchi/${id}`);
        }
      } else {
        const res = await purchaseApi.create(buildPayload(action === 'draft'));
        toast.success(action === 'draft' ? 'Kacchi Purchase saved as draft' : 'Kacchi Purchase posted successfully');
        navigate(`/purchases/kacchi/${res.data.id}`);
      }
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save Kacchi Purchase');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
      setConfirmPostOpen(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (pendingAction === 'post') {
      setConfirmPostOpen(true);
      return;
    }
    runSubmit(pendingAction || 'draft');
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
      <BackButton label="Back to Kacchi Purchases" onClick={() => guardedNavigate('/purchases/kacchi')} />

      <PageHeader
        title={isEdit ? 'Edit Kacchi Purchase' : 'New Kacchi Purchase / Purchase Challan'}
        description="GST is calculated in full, exactly like a normal GST purchase. This challan is simply excluded from GST return reporting."
      />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Alert>
        <AlertDescription>
          <strong>GST Calculated: YES</strong> &nbsp;·&nbsp; <strong>GST Reporting: NO</strong>
        </AlertDescription>
      </Alert>

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Tax Mode</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Supplier &amp; Challan Details</CardTitle>
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
              <Label htmlFor="purchaseDate">Challan Date</Label>
              <Input id="purchaseDate" type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} invalid={!!fieldErrors.purchaseDate} />
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
            <CardTitle>Challan Items</CardTitle>
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
                    <TableHead className="w-20">GST %</TableHead>
                    <TableHead className="w-28 text-right">Taxable</TableHead>
                    <TableHead className="w-24 text-right">GST Amt</TableHead>
                    <TableHead className="w-28 text-right">Total</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row, index) => {
                    const calc = rowCalc(row);
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
                                  {p.name}{p.sku ? ` [${p.sku}]` : ''} ({p.unit})
                                  {p.status === 'INACTIVE' ? ' (Inactive)' : ''}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </TableCell>
                        <TableCell>
                          <Input type="number" min={1} value={row.quantity} onChange={(e) => updateItem(index, 'quantity', e.target.value)} />
                        </TableCell>
                        <TableCell>
                          <Input type="number" min={0} step="0.01" value={row.purchasePrice} onChange={(e) => updateItem(index, 'purchasePrice', e.target.value)} />
                        </TableCell>
                        <TableCell>
                          <Input type="number" min={0} step="0.01" value={row.discount} onChange={(e) => updateItem(index, 'discount', e.target.value)} />
                        </TableCell>
                        <TableCell>
                          <Input type="number" min={0} step="0.01" value={row.gstPercent} onChange={(e) => updateItem(index, 'gstPercent', e.target.value)} />
                        </TableCell>
                        <TableCell className="text-right">{calc.taxable.toFixed(2)}</TableCell>
                        <TableCell className="text-right">{calc.gst.toFixed(2)}</TableCell>
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
                {taxMode === 'INTRA_STATE' ? (
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
                ) : (
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
              <p className="flex h-9 items-center rounded-md border border-border bg-muted px-3 text-sm font-medium">{dueAmount.toFixed(2)}</p>
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-wrap gap-2">
          <Button
            type="submit"
            variant="outline"
            loading={submitting && pendingAction === 'draft'}
            onClick={() => setPendingAction('draft')}
          >
            Save as Draft
          </Button>
          <Button type="submit" loading={submitting && pendingAction === 'post'} onClick={() => setPendingAction('post')}>
            {isEdit ? 'Save & Post' : 'Post Now'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => guardedNavigate('/purchases/kacchi')}>
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
        open={confirmPostOpen}
        title="Post this Kacchi Purchase?"
        description="Posting will add stock, update the supplier ledger, and post the accounting journal. This cannot be undone from here."
        confirmLabel="Post"
        cancelLabel="Cancel"
        destructive={false}
        onConfirm={() => runSubmit('post')}
        onCancel={() => setConfirmPostOpen(false)}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this Kacchi Purchase. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
