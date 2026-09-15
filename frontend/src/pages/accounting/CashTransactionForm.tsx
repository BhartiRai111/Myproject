import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { cashTransactionApi } from '../../api/cashTransactionApi';
import { parseApiError } from '../../utils/apiError';
import { PaymentMode } from '../../types/sale';
import { CashTransactionType } from '../../types/cashTransaction';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';

const PAYMENT_MODES: PaymentMode[] = ['CASH', 'BANK', 'UPI', 'CARD', 'OTHER'];

export default function CashTransactionForm() {
  const navigate = useNavigate();

  const [transactionDate, setTransactionDate] = useState(new Date().toISOString().slice(0, 10));
  const [transactionType, setTransactionType] = useState<CashTransactionType>('CASH_IN');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (post: boolean) => {
    const amt = Number(amount);
    if (!amt || amt <= 0) {
      setError('Amount must be greater than 0');
      return;
    }
    if (!reason.trim()) {
      setError('Reason is required');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      const res = await cashTransactionApi.create({
        transactionDate,
        transactionType,
        paymentMode,
        amount: amt,
        reason: reason.trim(),
        post,
      });
      toast.success(`Cash transaction ${res.data.transactionNumber} ${post ? 'posted' : 'saved as draft'}`);
      navigate(`/accounting/cash-transactions/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to create cash transaction').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Cash Management" onClick={() => navigate('/accounting/cash-transactions')} />

      <PageHeader title="New Cash Entry" description="Record a Cash In or Cash Out transaction." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Transaction Type</Label>
              <Select value={transactionType} onValueChange={(v) => setTransactionType(v as CashTransactionType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH_IN">Cash In</SelectItem>
                  <SelectItem value="CASH_OUT">Cash Out</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Transaction Date</Label>
              <Input type="date" value={transactionDate} onChange={(e) => setTransactionDate(e.target.value)} />
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
              <Label>Amount</Label>
              <Input type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Reason</Label>
            <Textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              placeholder="e.g. Petty cash withdrawal, cash deposit from till, owner's contribution"
            />
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
