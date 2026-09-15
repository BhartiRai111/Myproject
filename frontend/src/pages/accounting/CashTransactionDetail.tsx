import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { cashTransactionApi } from '../../api/cashTransactionApi';
import { parseApiError } from '../../utils/apiError';
import { CashTransaction } from '../../types/cashTransaction';
import { useAuth } from '@/context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function statusVariant(status: string) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function CashTransactionDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [txn, setTxn] = useState<CashTransaction | null>(null);
  const [loading, setLoading] = useState(true);
  const [posting, setPosting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [postConfirm, setPostConfirm] = useState(false);
  const [cancelConfirm, setCancelConfirm] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await cashTransactionApi.getById(Number(id));
      setTxn(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load cash transaction').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handlePost = async () => {
    if (!txn) return;
    setPosting(true);
    try {
      await cashTransactionApi.post(txn.id);
      toast.success(`Cash transaction ${txn.transactionNumber} posted`);
      setPostConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to post cash transaction').message);
      setPostConfirm(false);
    } finally {
      setPosting(false);
    }
  };

  const handleCancel = async () => {
    if (!txn) return;
    setCancelling(true);
    try {
      await cashTransactionApi.cancel(txn.id);
      toast.success(`Cash transaction ${txn.transactionNumber} cancelled`);
      setCancelConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel cash transaction').message);
      setCancelConfirm(false);
    } finally {
      setCancelling(false);
    }
  };

  if (loading || !txn) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <BackButton label="Back to Cash Management" onClick={() => navigate('/accounting/cash-transactions')} />

      <PageHeader
        title={txn.transactionNumber}
        description={`${txn.transactionType === 'CASH_IN' ? 'Cash In' : 'Cash Out'} — dated ${txn.transactionDate}`}
        actions={
          <div className="flex gap-2">
            {canManage && txn.status === 'DRAFT' && <Button onClick={() => setPostConfirm(true)}>Post</Button>}
            {canManage && txn.status !== 'CANCELLED' && (
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
            <Badge variant={statusVariant(txn.status)}>{txn.status}</Badge>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Type</p>
            <p className="font-medium">{txn.transactionType === 'CASH_IN' ? 'Cash In' : 'Cash Out'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Payment Mode</p>
            <p className="font-medium">{txn.paymentMode}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Amount</p>
            <p className="font-medium">{money(txn.amount)}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Posted By</p>
            <p className="font-medium">{txn.postedBy || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Created By</p>
            <p className="font-medium">{txn.createdBy || '—'}</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-5 text-sm">
          <p className="text-xs text-muted-foreground">Reason</p>
          <p>{txn.reason}</p>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={postConfirm}
        title={`Post Cash Transaction ${txn.transactionNumber}?`}
        description="This will post the accounting journal for this cash movement. This cannot be undone directly — only via cancellation."
        confirmLabel={posting ? 'Posting...' : 'Post'}
        destructive={false}
        onConfirm={handlePost}
        onCancel={() => setPostConfirm(false)}
      />

      <ConfirmDialog
        open={cancelConfirm}
        title={`Cancel Cash Transaction ${txn.transactionNumber}?`}
        description="This will reverse the accounting journal if this transaction was posted."
        confirmLabel={cancelling ? 'Cancelling...' : 'Cancel Transaction'}
        cancelLabel="Keep Transaction"
        onConfirm={handleCancel}
        onCancel={() => setCancelConfirm(false)}
      />
    </div>
  );
}
