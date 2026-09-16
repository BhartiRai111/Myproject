import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { expenseApi } from '../../api/expenseApi';
import { expenseCategoryApi } from '../../api/mastersApi';
import { supplierApi } from '../../api/supplierApi';
import { parseApiError } from '../../utils/apiError';
import { PaymentMode, TaxMode } from '../../types/sale';
import { ExpenseCategory } from '../../types/masters';
import { Supplier } from '../../types/supplier';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const PAYMENT_MODES: PaymentMode[] = ['CASH', 'BANK', 'UPI', 'CARD', 'OTHER'];

export default function ExpenseForm() {
  const navigate = useNavigate();
  const { id } = useParams();
  const isEdit = !!id;

  const [loading, setLoading] = useState(isEdit);
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);

  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().slice(0, 10));
  const [categoryId, setCategoryId] = useState('');
  const [isCreditExpense, setIsCreditExpense] = useState(false);
  const [supplierId, setSupplierId] = useState('');
  const [vendorName, setVendorName] = useState('');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [grossAmount, setGrossAmount] = useState('');
  const [hasDiscount, setHasDiscount] = useState(false);
  const [discountAmount, setDiscountAmount] = useState('');
  const [hasGst, setHasGst] = useState(false);
  const [gstPercent, setGstPercent] = useState('');
  const [taxMode, setTaxMode] = useState<TaxMode | ''>('');
  const [itcEligible, setItcEligible] = useState(false);
  const [description, setDescription] = useState('');
  const [referenceNumber, setReferenceNumber] = useState('');
  const [remarks, setRemarks] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    Promise.all([expenseCategoryApi.listActive(), supplierApi.list({ size: 200 })]).then(([catRes, supRes]) => {
      setCategories(catRes.data);
      setSuppliers(supRes.data.content);
    });
  }, []);

  useEffect(() => {
    if (!isEdit) return;
    expenseApi
      .getById(Number(id))
      .then((res) => {
        const e = res.data;
        if (e.status !== 'DRAFT') {
          toast.error('Only a DRAFT expense can be edited');
          navigate(`/accounting/expenses/${e.id}`);
          return;
        }
        setExpenseDate(e.expenseDate);
        setCategoryId(e.categoryId ? String(e.categoryId) : '');
        setIsCreditExpense(!!e.supplierId);
        setSupplierId(e.supplierId ? String(e.supplierId) : '');
        setVendorName(e.vendorName || '');
        setPaymentMode(e.paymentMode);
        const gross = e.taxableAmount + (e.discountAmount || 0);
        setGrossAmount(String(gross));
        if (e.discountAmount) {
          setHasDiscount(true);
          setDiscountAmount(String(e.discountAmount));
        }
        if (e.taxMode) {
          setHasGst(true);
          setTaxMode(e.taxMode);
          setGstPercent(String(e.gstPercent || 0));
          setItcEligible(e.itcEligible);
        }
        setDescription(e.description || '');
        setReferenceNumber(e.referenceNumber || '');
        setRemarks(e.remarks || '');
      })
      .catch((err) => toast.error(parseApiError(err, 'Failed to load expense').message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const preview = useMemo(() => {
    const gross = Number(grossAmount) || 0;
    const discount = hasDiscount ? Number(discountAmount) || 0 : 0;
    const taxable = Math.max(0, gross - discount);
    const pct = hasGst ? Number(gstPercent) || 0 : 0;
    const gstAmount = pct > 0 ? Math.round(((taxable * pct) / 100) * 100) / 100 : 0;
    let cgst = 0;
    let sgst = 0;
    let igst = 0;
    if (gstAmount > 0) {
      if (taxMode === 'INTER_STATE') {
        igst = gstAmount;
      } else {
        cgst = Math.round((gstAmount / 2) * 100) / 100;
        sgst = Math.round((gstAmount - cgst) * 100) / 100;
      }
    }
    return { gross, discount, taxable, gstAmount, cgst, sgst, igst, total: taxable + gstAmount };
  }, [grossAmount, hasDiscount, discountAmount, hasGst, gstPercent, taxMode]);

  const handleSubmit = async (post: boolean) => {
    if (!categoryId) {
      setError('Category is required');
      return;
    }
    if (isCreditExpense && !supplierId) {
      setError('Select a supplier for a credit expense, or switch back to Cash/Bank payment');
      return;
    }
    const gross = Number(grossAmount);
    if (!gross || gross <= 0) {
      setError('Amount must be greater than 0');
      return;
    }
    if (hasGst && Number(gstPercent) > 0 && !taxMode) {
      setError('Select Intra-State or Inter-State when GST is applicable');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      const payload = {
        expenseDate,
        categoryId: Number(categoryId),
        supplierId: isCreditExpense && supplierId ? Number(supplierId) : undefined,
        vendorName: !isCreditExpense && vendorName.trim() ? vendorName.trim() : undefined,
        paymentMode,
        taxMode: hasGst ? taxMode || undefined : undefined,
        gstPercent: hasGst ? Number(gstPercent) || 0 : undefined,
        itcEligible: hasGst ? itcEligible : false,
        grossAmount: gross,
        discountAmount: hasDiscount ? Number(discountAmount) || 0 : 0,
        description: description.trim() || undefined,
        referenceNumber: referenceNumber.trim() || undefined,
        remarks: remarks.trim() || undefined,
        post,
      };

      const res = isEdit ? await expenseApi.update(Number(id), payload) : await expenseApi.create(payload);
      toast.success(`Expense ${res.data.expenseNumber} ${post ? 'posted' : 'saved as draft'}`);
      navigate(`/accounting/expenses/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to save expense').message);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <BackButton label="Back to Expenses" onClick={() => navigate('/accounting/expenses')} />

      <PageHeader title={isEdit ? 'Edit Expense' : 'New Expense'} description="Record an operational business expense." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label>Expense Date</Label>
              <Input type="date" value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Category</Label>
              <Select value={categoryId || undefined} onValueChange={setCategoryId}>
                <SelectTrigger>
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
            </div>
            <div className="space-y-1.5">
              <Label>Reference Number (optional)</Label>
              <Input value={referenceNumber} onChange={(e) => setReferenceNumber(e.target.value)} placeholder="Bill / invoice ref." />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-input accent-primary"
              checked={isCreditExpense}
              onChange={(e) => {
                setIsCreditExpense(e.target.checked);
                if (!e.target.checked) setSupplierId('');
              }}
            />
            This is a credit expense (payable to a party — settle later via Payment)
          </label>

          {isCreditExpense ? (
            <div className="space-y-1.5">
              <Label>Party (Supplier)</Label>
              <Select value={supplierId || undefined} onValueChange={setSupplierId}>
                <SelectTrigger>
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
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label>Vendor (optional)</Label>
                <Input value={vendorName} onChange={(e) => setVendorName(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label>Payment Mode</Label>
                <Select value={paymentMode} onValueChange={(v) => setPaymentMode(v as PaymentMode)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PAYMENT_MODES.map((m) => (
                      <SelectItem key={m} value={m}>
                        {m}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Amount</Label>
              <Input type="number" min={0} step="0.01" value={grossAmount} onChange={(e) => setGrossAmount(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <label className="flex items-center gap-2 pb-1.5 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-input accent-primary"
                  checked={hasDiscount}
                  onChange={(e) => setHasDiscount(e.target.checked)}
                />
                Discount applies
              </label>
              {hasDiscount && (
                <Input type="number" min={0} step="0.01" value={discountAmount} onChange={(e) => setDiscountAmount(e.target.value)} />
              )}
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-input accent-primary"
              checked={hasGst}
              onChange={(e) => setHasGst(e.target.checked)}
            />
            This expense has GST
          </label>

          {hasGst && (
            <div className="grid grid-cols-1 gap-3 rounded-lg border border-border p-3 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label>GST %</Label>
                <Input type="number" min={0} step="0.01" value={gstPercent} onChange={(e) => setGstPercent(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label>Tax Mode</Label>
                <Select value={taxMode || undefined} onValueChange={(v) => setTaxMode(v as TaxMode)}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INTRA_STATE">Intra-State (CGST+SGST)</SelectItem>
                    <SelectItem value="INTER_STATE">Inter-State (IGST)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <label className="flex items-end gap-2 pb-2 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-input accent-primary"
                  checked={itcEligible}
                  onChange={(e) => setItcEligible(e.target.checked)}
                />
                Input Tax Credit (ITC) eligible
              </label>
            </div>
          )}

          <div className="space-y-1.5">
            <Label>Description (optional)</Label>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
          </div>
          <div className="space-y-1.5">
            <Label>Remarks (optional)</Label>
            <Textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col items-end gap-1 p-5 text-sm">
          <div className="flex w-full max-w-xs justify-between text-muted-foreground">
            <span>Amount</span>
            <span>{money(preview.gross)}</span>
          </div>
          {hasDiscount && (
            <div className="flex w-full max-w-xs justify-between text-muted-foreground">
              <span>Discount</span>
              <span>- {money(preview.discount)}</span>
            </div>
          )}
          <div className="flex w-full max-w-xs justify-between text-muted-foreground">
            <span>Taxable Amount</span>
            <span>{money(preview.taxable)}</span>
          </div>
          {hasGst && (
            <>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>CGST</span>
                <span>{money(preview.cgst)}</span>
              </div>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>SGST</span>
                <span>{money(preview.sgst)}</span>
              </div>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>IGST</span>
                <span>{money(preview.igst)}</span>
              </div>
            </>
          )}
          <Separator className="my-1 w-full max-w-xs" />
          <div className="flex w-full max-w-xs justify-between text-base font-semibold">
            <span>Total</span>
            <span>{money(preview.total)}</span>
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end gap-2">
        <Button variant="outline" loading={submitting} onClick={() => handleSubmit(false)}>
          Save as Draft
        </Button>
        <Button loading={submitting} onClick={() => handleSubmit(true)}>
          Save and Post
        </Button>
      </div>
    </div>
  );
}
