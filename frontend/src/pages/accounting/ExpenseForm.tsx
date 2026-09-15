import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { expenseApi } from '../../api/expenseApi';
import { parseApiError } from '../../utils/apiError';
import { PaymentMode, TaxMode } from '../../types/sale';
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

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const PAYMENT_MODES: PaymentMode[] = ['CASH', 'BANK', 'UPI', 'CARD', 'OTHER'];

export default function ExpenseForm() {
  const navigate = useNavigate();

  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().slice(0, 10));
  const [category, setCategory] = useState('');
  const [vendorName, setVendorName] = useState('');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [taxableAmount, setTaxableAmount] = useState('');
  const [hasGst, setHasGst] = useState(false);
  const [gstPercent, setGstPercent] = useState('');
  const [taxMode, setTaxMode] = useState<TaxMode | ''>('');
  const [itcEligible, setItcEligible] = useState(false);
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const preview = useMemo(() => {
    const taxable = Number(taxableAmount) || 0;
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
    return { taxable, gstAmount, cgst, sgst, igst, total: taxable + gstAmount };
  }, [taxableAmount, hasGst, gstPercent, taxMode]);

  const handleSubmit = async (post: boolean) => {
    if (!category.trim()) {
      setError('Category is required');
      return;
    }
    const taxable = Number(taxableAmount);
    if (!taxable || taxable <= 0) {
      setError('Taxable amount must be greater than 0');
      return;
    }
    if (hasGst && Number(gstPercent) > 0 && !taxMode) {
      setError('Select Intra-State or Inter-State when GST is applicable');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      const res = await expenseApi.create({
        expenseDate,
        category: category.trim(),
        vendorName: vendorName.trim() || undefined,
        paymentMode,
        taxMode: hasGst ? taxMode || undefined : undefined,
        gstPercent: hasGst ? Number(gstPercent) || 0 : undefined,
        itcEligible: hasGst ? itcEligible : false,
        taxableAmount: taxable,
        description: description.trim() || undefined,
        post,
      });
      toast.success(`Expense ${res.data.expenseNumber} ${post ? 'posted' : 'saved as draft'}`);
      navigate(`/accounting/expenses/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to create expense').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Expenses" onClick={() => navigate('/accounting/expenses')} />

      <PageHeader title="New Expense" description="Record an operational business expense." />

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
              <Input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="e.g. Rent, Electricity, Transport" />
            </div>
            <div className="space-y-1.5">
              <Label>Vendor (optional)</Label>
              <Input value={vendorName} onChange={(e) => setVendorName(e.target.value)} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
            <div className="space-y-1.5">
              <Label>Taxable Amount</Label>
              <Input
                type="number"
                min={0}
                step="0.01"
                value={taxableAmount}
                onChange={(e) => setTaxableAmount(e.target.value)}
              />
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
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col items-end gap-1 p-5 text-sm">
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
