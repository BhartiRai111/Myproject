import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Ban, CheckCircle2, PackageCheck, Truck } from 'lucide-react';
import { stockTransferApi } from '../../api/stockTransferApi';
import { parseApiError } from '../../utils/apiError';
import { StockTransfer, StockTransferStatus } from '../../types/stockTransfer';
import { useAuth } from '../../context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function statusVariant(status: StockTransferStatus) {
  if (status === 'RECEIVED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  if (status === 'DISPATCHED') return 'warning' as const;
  if (status === 'APPROVED') return 'secondary' as const;
  return 'muted' as const;
}

export default function StockTransferDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();

  const [transfer, setTransfer] = useState<StockTransfer | null>(null);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await stockTransferApi.getById(Number(id));
      setTransfer(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load stock transfer').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const runAction = async (action: () => Promise<unknown>, successMessage: string) => {
    setActing(true);
    try {
      await action();
      toast.success(successMessage);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Action failed').message);
    } finally {
      setActing(false);
    }
  };

  const handleCancelConfirm = async () => {
    if (!transfer) return;
    setCancelOpen(false);
    await runAction(() => stockTransferApi.cancel(transfer.id), 'Transfer cancelled');
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!transfer) {
    return <p className="text-sm text-muted-foreground">Stock transfer not found.</p>;
  }

  return (
    <div className="space-y-6">
      <BackButton label="Back to Stock Transfers" onClick={() => navigate('/inventory/stock-transfers')} />

      <PageHeader
        title={transfer.transferNumber}
        description={`${transfer.fromStoreName} → ${transfer.toStoreName}`}
        actions={
          <div className="flex flex-wrap gap-2">
            {transfer.status === 'DRAFT' && hasPermission('STOCK_TRANSFER_APPROVE') && (
              <Button loading={acting} onClick={() => runAction(() => stockTransferApi.approve(transfer.id), 'Transfer approved')}>
                <CheckCircle2 className="h-4 w-4" /> Approve
              </Button>
            )}
            {transfer.status === 'APPROVED' && hasPermission('STOCK_TRANSFER_DISPATCH') && (
              <Button loading={acting} onClick={() => runAction(() => stockTransferApi.dispatch(transfer.id), 'Transfer dispatched')}>
                <Truck className="h-4 w-4" /> Dispatch
              </Button>
            )}
            {transfer.status === 'DISPATCHED' && hasPermission('STOCK_TRANSFER_RECEIVE') && (
              <Button loading={acting} onClick={() => runAction(() => stockTransferApi.receive(transfer.id), 'Transfer received')}>
                <PackageCheck className="h-4 w-4" /> Receive
              </Button>
            )}
            {(transfer.status === 'DRAFT' || transfer.status === 'APPROVED') && hasPermission('STOCK_TRANSFER_CANCEL') && (
              <Button variant="destructive" loading={acting} onClick={() => setCancelOpen(true)}>
                <Ban className="h-4 w-4" /> Cancel
              </Button>
            )}
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Transfer Details <Badge variant={statusVariant(transfer.status)}>{transfer.status}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">Transfer Date</p>
            <p className="font-medium">{transfer.transferDate}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">From Store</p>
            <p className="font-medium">
              {transfer.fromStoreName} ({transfer.fromStoreCode})
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">To Store</p>
            <p className="font-medium">
              {transfer.toStoreName} ({transfer.toStoreCode})
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Created By</p>
            <p className="font-medium">{transfer.createdBy || '-'}</p>
          </div>
          {transfer.remarks && (
            <div className="sm:col-span-2 lg:col-span-4">
              <p className="text-xs text-muted-foreground">Remarks</p>
              <p className="font-medium">{transfer.remarks}</p>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Items</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Product</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead className="text-right">Quantity</TableHead>
                <TableHead>Notes</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {transfer.items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-medium">{item.productName}</TableCell>
                  <TableCell className="text-muted-foreground">{item.sku || '-'}</TableCell>
                  <TableCell className="text-right">{item.quantity}</TableCell>
                  <TableCell className="text-muted-foreground">{item.notes || '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Status Timeline</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Created</span>
            <span>
              {transfer.createdBy} &mdash; {new Date(transfer.createdAt).toLocaleString()}
            </span>
          </div>
          {transfer.approvedAt && (
            <>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Approved</span>
                <span>
                  {transfer.approvedBy} &mdash; {new Date(transfer.approvedAt).toLocaleString()}
                </span>
              </div>
            </>
          )}
          {transfer.dispatchedAt && (
            <>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Dispatched</span>
                <span>
                  {transfer.dispatchedBy} &mdash; {new Date(transfer.dispatchedAt).toLocaleString()}
                </span>
              </div>
            </>
          )}
          {transfer.receivedAt && (
            <>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Received</span>
                <span>
                  {transfer.receivedBy} &mdash; {new Date(transfer.receivedAt).toLocaleString()}
                </span>
              </div>
            </>
          )}
          {transfer.cancelledAt && (
            <>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Cancelled</span>
                <span>
                  {transfer.cancelledBy} &mdash; {new Date(transfer.cancelledAt).toLocaleString()}
                </span>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={cancelOpen}
        title="Cancel Stock Transfer"
        description={`Are you sure you want to cancel transfer ${transfer.transferNumber}? This cannot be undone.`}
        onConfirm={handleCancelConfirm}
        onCancel={() => setCancelOpen(false)}
      />
    </div>
  );
}
