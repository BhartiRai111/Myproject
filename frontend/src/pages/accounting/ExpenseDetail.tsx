import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Pencil, Printer } from 'lucide-react';
import { expenseApi } from '../../api/expenseApi';
import { parseApiError } from '../../utils/apiError';
import { Expense } from '../../types/expense';
import { useAuth } from '@/context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function statusVariant(status: string) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

function paymentStatusVariant(status?: string | null) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'muted' as const;
}

export default function ExpenseDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [expense, setExpense] = useState<Expense | null>(null);
  const [loading, setLoading] = useState(true);
  const [posting, setPosting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [postConfirm, setPostConfirm] = useState(false);
  const [cancelConfirm, setCancelConfirm] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await expenseApi.getById(Number(id));
      setExpense(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load expense').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handlePost = async () => {
    if (!expense) return;
    setPosting(true);
    try {
      await expenseApi.post(expense.id);
      toast.success(`Expense ${expense.expenseNumber} posted`);
      setPostConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to post expense').message);
      setPostConfirm(false);
    } finally {
      setPosting(false);
    }
  };

  const handleCancel = async () => {
    if (!expense) return;
    setCancelling(true);
    try {
      await expenseApi.cancel(expense.id);
      toast.success(`Expense ${expense.expenseNumber} cancelled`);
      setCancelConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel expense').message);
      setCancelConfirm(false);
    } finally {
      setCancelling(false);
    }
  };

  if (loading || !expense) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const hasGst = expense.taxMode != null;
  const isCreditExpense = !!expense.supplierId;

  return (
    <div className="space-y-6">
      <BackButton label="Back to Expenses" onClick={() => navigate('/accounting/expenses')} />

      <PageHeader
        title={expense.expenseNumber}
        description={`${expense.category} — dated ${expense.expenseDate}`}
        actions={
          <div className="flex gap-2 print:hidden">
            <Button variant="outline" onClick={() => window.print()}>
              <Printer className="h-4 w-4" /> Print
            </Button>
            {canManage && expense.status === 'DRAFT' && (
              <Button variant="outline" onClick={() => navigate(`/accounting/expenses/${expense.id}/edit`)}>
                <Pencil className="h-4 w-4" /> Edit
              </Button>
            )}
            {canManage && expense.status === 'DRAFT' && <Button onClick={() => setPostConfirm(true)}>Post</Button>}
            {canManage && expense.status !== 'CANCELLED' && (
              <Button variant="destructive" onClick={() => setCancelConfirm(true)}>
                Cancel
              </Button>
            )}
          </div>
        }
      />

      <Card>
        <CardContent className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">Status</p>
            <Badge variant={statusVariant(expense.status)}>{expense.status}</Badge>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Category</p>
            <p className="font-medium">{expense.category}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">{isCreditExpense ? 'Party (Supplier)' : 'Vendor'}</p>
            <p className="font-medium">{isCreditExpense ? expense.vendorName : expense.vendorName || '—'}</p>
            {isCreditExpense && expense.supplierGstin && <p className="text-xs text-muted-foreground">{expense.supplierGstin}</p>}
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Payment Mode</p>
            <p className="font-medium">{isCreditExpense ? 'Credit (Party Payable)' : expense.paymentMode}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">ITC Eligible</p>
            <p className="font-medium">{hasGst ? (expense.itcEligible ? 'Yes' : 'No') : '—'}</p>
          </div>
          {isCreditExpense && (
            <div>
              <p className="text-xs text-muted-foreground">Payment Status</p>
              <Badge variant={paymentStatusVariant(expense.paymentStatus)}>{expense.paymentStatus || '—'}</Badge>
            </div>
          )}
          <div>
            <p className="text-xs text-muted-foreground">Posted By</p>
            <p className="font-medium">{expense.postedBy || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Created By</p>
            <p className="font-medium">{expense.createdBy || '—'}</p>
          </div>
          {expense.referenceNumber && (
            <div>
              <p className="text-xs text-muted-foreground">Reference Number</p>
              <p className="font-medium">{expense.referenceNumber}</p>
            </div>
          )}
        </CardContent>
      </Card>

      {isCreditExpense && expense.status === 'POSTED' && (
        <Card>
          <CardContent className="flex items-center justify-between p-5 text-sm">
            <span className="text-muted-foreground">Outstanding Payable to {expense.vendorName}</span>
            <span className="text-base font-semibold">{money(expense.payableAmount)}</span>
          </CardContent>
        </Card>
      )}

      {(expense.description || expense.remarks) && (
        <Card>
          <CardContent className="space-y-3 p-5 text-sm">
            {expense.description && (
              <div>
                <p className="text-xs text-muted-foreground">Description</p>
                <p>{expense.description}</p>
              </div>
            )}
            {expense.remarks && (
              <div>
                <p className="text-xs text-muted-foreground">Remarks</p>
                <p>{expense.remarks}</p>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="flex flex-col items-end gap-1 p-5 text-sm">
          {!!expense.discountAmount && (
            <>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>Amount</span>
                <span>{money(expense.taxableAmount + expense.discountAmount)}</span>
              </div>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>Discount</span>
                <span>- {money(expense.discountAmount)}</span>
              </div>
            </>
          )}
          <div className="flex w-full max-w-xs justify-between text-muted-foreground">
            <span>Taxable Amount</span>
            <span>{money(expense.taxableAmount)}</span>
          </div>
          {hasGst && (
            <>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>CGST</span>
                <span>{money(expense.cgstAmount)}</span>
              </div>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>SGST</span>
                <span>{money(expense.sgstAmount)}</span>
              </div>
              <div className="flex w-full max-w-xs justify-between text-muted-foreground">
                <span>IGST</span>
                <span>{money(expense.igstAmount)}</span>
              </div>
            </>
          )}
          <Separator className="my-1 w-full max-w-xs" />
          <div className="flex w-full max-w-xs justify-between text-base font-semibold">
            <span>Total Amount</span>
            <span>{money(expense.totalAmount)}</span>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={postConfirm}
        title={`Post Expense ${expense.expenseNumber}?`}
        description={
          isCreditExpense
            ? 'This will debit the Expense account (and Input GST accounts if ITC eligible) and credit Supplier Payable via the accounting journal. Settle it later from Payment Entry.'
            : 'This will debit the Expense account (and Input GST accounts if ITC eligible) and credit Cash/Bank via the accounting journal. This cannot be undone directly — only via cancellation.'
        }
        confirmLabel={posting ? 'Posting...' : 'Post'}
        destructive={false}
        onConfirm={handlePost}
        onCancel={() => setPostConfirm(false)}
      />

      <ConfirmDialog
        open={cancelConfirm}
        title={`Cancel Expense ${expense.expenseNumber}?`}
        description="This will reverse the accounting journal (and party payable, if applicable) if this expense was posted."
        confirmLabel={cancelling ? 'Cancelling...' : 'Cancel Expense'}
        cancelLabel="Keep Expense"
        onConfirm={handleCancel}
        onCancel={() => setCancelConfirm(false)}
      />
    </div>
  );
}
