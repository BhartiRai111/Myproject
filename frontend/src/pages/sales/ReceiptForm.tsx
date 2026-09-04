import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { customerApi } from '../../api/customerApi';
import { receiptApi } from '../../api/receiptApi';
import { parseApiError } from '../../utils/apiError';
import { Customer, PaymentMode } from '../../types/sale';
import { OutstandingBill, ReceiptPayload } from '../../types/receipt';
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

interface AllocationRow {
  bill: OutstandingBill;
  checked: boolean;
  amount: string;
}

export default function ReceiptForm() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialCustomerId = searchParams.get('customerId');

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customerId, setCustomerId] = useState(initialCustomerId || '');
  const [receiptDate, setReceiptDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [amount, setAmount] = useState('0');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [remarks, setRemarks] = useState('');
  const [outstanding, setOutstanding] = useState<AllocationRow[]>([]);
  const [totalOutstanding, setTotalOutstanding] = useState(0);
  const [loadingOutstanding, setLoadingOutstanding] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);

  const initialSnapshot = useRef<string | null>(null);
  const getSnapshot = () => JSON.stringify({ customerId, amount, paymentMode, remarks });
  const { guardedNavigate, confirmOpen, confirmLeave, cancelLeave } = useUnsavedChangesGuard(
    () => initialSnapshot.current !== null && getSnapshot() !== initialSnapshot.current
  );

  useEffect(() => {
    customerApi.list().then((res) => {
      setCustomers(res.data);
      setLoading(false);
    });
  }, []);

  useEffect(() => {
    if (!loading && initialSnapshot.current === null) {
      initialSnapshot.current = getSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  useEffect(() => {
    if (!customerId) {
      setOutstanding([]);
      setTotalOutstanding(0);
      return;
    }
    setLoadingOutstanding(true);
    receiptApi
      .getOutstanding(Number(customerId))
      .then((res) => {
        setTotalOutstanding(res.data.totalOutstanding);
        setOutstanding(res.data.bills.map((bill) => ({ bill, checked: false, amount: String(bill.dueAmount) })));
      })
      .finally(() => setLoadingOutstanding(false));
  }, [customerId]);

  const toggleRow = (index: number) => {
    setOutstanding((prev) => prev.map((row, i) => (i === index ? { ...row, checked: !row.checked } : row)));
  };

  const updateRowAmount = (index: number, value: string) => {
    setOutstanding((prev) => prev.map((row, i) => (i === index ? { ...row, amount: value } : row)));
  };

  const checkedTotal = outstanding.filter((r) => r.checked).reduce((sum, r) => sum + toNumber(r.amount), 0);

  const validate = (): string | null => {
    if (!customerId) return 'Customer is required';
    if (toNumber(amount) <= 0) return 'Receipt amount must be greater than 0';
    const checkedRows = outstanding.filter((r) => r.checked);
    for (const row of checkedRows) {
      if (toNumber(row.amount) <= 0) return `Allocation for ${row.bill.invoiceNumber} must be greater than 0`;
      if (toNumber(row.amount) > row.bill.dueAmount) {
        return `Allocation for ${row.bill.invoiceNumber} cannot exceed its due amount of ${row.bill.dueAmount.toFixed(2)}`;
      }
    }
    if (checkedRows.length > 0 && checkedTotal > toNumber(amount)) {
      return 'Total allocated amount cannot exceed the receipt amount';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const checkedRows = outstanding.filter((r) => r.checked);
    const payload: ReceiptPayload = {
      customerId: Number(customerId),
      receiptDate,
      amount: toNumber(amount),
      paymentMode,
      remarks: remarks || undefined,
      allocations: checkedRows.length > 0
        ? checkedRows.map((r) => ({ saleId: r.bill.saleId, amountApplied: toNumber(r.amount) }))
        : [],
    };

    setSubmitting(true);
    try {
      const res = await receiptApi.create(payload);
      toast.success('Receipt recorded successfully');
      navigate(`/sales/receipts/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to save receipt').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Receipts" onClick={() => guardedNavigate('/sales/receipts')} />

      <PageHeader title="New Receipt" description="Record a payment received from a customer." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Receipt Details</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-1.5">
              <Label>Customer</Label>
              <Select value={customerId} onValueChange={setCustomerId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select customer" />
                </SelectTrigger>
                <SelectContent>
                  {customers.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {c.firstName} {c.lastName || ''}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Previous Outstanding</Label>
              <p className="flex h-9 items-center rounded-md border border-border bg-muted px-3 text-sm font-medium">
                {totalOutstanding.toFixed(2)}
              </p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="receiptDate">Receipt Date</Label>
              <Input id="receiptDate" type="date" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} />
            </div>
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
              <Label htmlFor="amount">Receipt Amount</Label>
              <Input id="amount" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
            <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
              <Label htmlFor="remarks">Remarks</Label>
              <Textarea id="remarks" rows={1} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        {customerId && (
          <Card>
            <CardHeader>
              <CardTitle>Outstanding Sales Bills</CardTitle>
            </CardHeader>
            <CardContent>
              {loadingOutstanding ? (
                <p className="text-sm text-muted-foreground">Loading outstanding bills...</p>
              ) : outstanding.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No outstanding bills for this customer. The receipt amount will be recorded as an on-account credit.
                </p>
              ) : (
                <>
                  <p className="mb-3 text-sm text-muted-foreground">
                    Optionally select which bill(s) this payment applies to. If none are selected, it will be
                    auto-applied to the oldest outstanding bills first.
                  </p>
                  <div className="overflow-hidden rounded-lg border border-border">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="w-10" />
                          <TableHead>Invoice</TableHead>
                          <TableHead>Date</TableHead>
                          <TableHead className="text-right">Total</TableHead>
                          <TableHead className="text-right">Due</TableHead>
                          <TableHead className="w-32 text-right">Apply Amount</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {outstanding.map((row, index) => (
                          <TableRow key={row.bill.saleId}>
                            <TableCell>
                              <input
                                type="checkbox"
                                className="h-4 w-4 rounded border-input accent-primary"
                                checked={row.checked}
                                onChange={() => toggleRow(index)}
                              />
                            </TableCell>
                            <TableCell className="font-medium">{row.bill.invoiceNumber}</TableCell>
                            <TableCell className="text-muted-foreground">{row.bill.saleDate}</TableCell>
                            <TableCell className="text-right">{row.bill.totalAmount.toFixed(2)}</TableCell>
                            <TableCell className="text-right">{row.bill.dueAmount.toFixed(2)}</TableCell>
                            <TableCell>
                              <Input
                                type="number"
                                min={0}
                                max={row.bill.dueAmount}
                                step="0.01"
                                disabled={!row.checked}
                                value={row.amount}
                                onChange={(e) => updateRowAmount(index, e.target.value)}
                                className="text-right"
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                  {checkedTotal > 0 && (
                    <p className="mt-2 text-right text-sm text-muted-foreground">
                      Selected allocation total: <span className="font-medium text-foreground">{checkedTotal.toFixed(2)}</span>
                    </p>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        )}

        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            Save Receipt
          </Button>
          <Button type="button" variant="outline" onClick={() => guardedNavigate('/sales/receipts')}>
            Cancel
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this receipt. Leaving now will discard them."
        onConfirm={confirmLeave}
        onCancel={cancelLeave}
      />
    </div>
  );
}
