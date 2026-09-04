import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { FilePlus, Pencil, XCircle } from 'lucide-react';
import { purchaseOrderApi } from '../../api/purchaseOrderApi';
import { parseApiError } from '../../utils/apiError';
import { PurchaseOrder, PurchaseOrderStatus } from '../../types/purchaseOrder';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function statusVariant(status: PurchaseOrderStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  if (status === 'PARTIALLY_RECEIVED') return 'warning' as const;
  return 'muted' as const;
}

export default function PurchaseOrderDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const load = () => {
    setLoading(true);
    purchaseOrderApi
      .getById(Number(id))
      .then((res) => setOrder(res.data))
      .catch((err) => toast.error(parseApiError(err, 'Failed to load purchase order').message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [id]);

  const handleCancel = async () => {
    setCancelling(true);
    try {
      await purchaseOrderApi.cancel(Number(id));
      toast.success('Purchase order cancelled');
      setCancelOpen(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel purchase order').message);
    } finally {
      setCancelling(false);
    }
  };

  if (loading || !order) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const canEdit = order.status === 'DRAFT' || order.status === 'CONFIRMED' || order.status === 'PARTIALLY_RECEIVED';
  const canBill = order.status !== 'COMPLETED' && order.status !== 'CANCELLED';
  const canCancel = order.status === 'DRAFT' || order.status === 'CONFIRMED';

  return (
    <div className="space-y-6">
      <BackButton label="Back to Purchase Orders" onClick={() => navigate('/purchases/orders')} />

      <PageHeader
        title={`Purchase Order ${order.orderNumber}`}
        description={`Placed on ${order.orderDate}`}
        actions={
          <div className="flex gap-2">
            {canBill && (
              <Button onClick={() => navigate(`/purchases/bills/new?orderId=${order.id}`)}>
                <FilePlus className="h-4 w-4" /> Create Purchase Bill
              </Button>
            )}
            {canEdit && (
              <Button variant="outline" onClick={() => navigate(`/purchases/orders/${order.id}/edit`)}>
                <Pencil className="h-4 w-4" /> Edit
              </Button>
            )}
            {canCancel && (
              <Button variant="destructive" onClick={() => setCancelOpen(true)}>
                <XCircle className="h-4 w-4" /> Cancel
              </Button>
            )}
          </div>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Status</p>
            <Badge variant={statusVariant(order.status)} className="mt-1">
              {order.status.replace('_', ' ')}
            </Badge>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Total Amount</p>
            <p className="mt-1 text-lg font-semibold">{order.totalAmount.toFixed(2)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Received Amount</p>
            <p className="mt-1 text-lg font-semibold">{order.receivedAmount.toFixed(2)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Remaining Amount</p>
            <p className="mt-1 text-lg font-semibold">{order.remainingAmount.toFixed(2)}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Supplier &amp; Delivery</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">Supplier</p>
            <p>{order.supplier ? order.supplier.name : '-'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Phone</p>
            <p>{order.supplierPhone || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">GSTIN</p>
            <p>{order.supplierGstin || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Expected Delivery</p>
            <p>{order.expectedDeliveryDate || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Billing Address</p>
            <p>{order.billingAddress || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Shipping Address</p>
            <p>{order.shippingAddress || '-'}</p>
          </div>
          <div className="sm:col-span-2 lg:col-span-2">
            <p className="text-xs text-muted-foreground">Remarks</p>
            <p>{order.remarks || '-'}</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Order Items</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-hidden rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead className="text-right">Qty</TableHead>
                  <TableHead className="text-right">Rate</TableHead>
                  <TableHead className="text-right">Discount</TableHead>
                  <TableHead className="text-right">GST %</TableHead>
                  <TableHead className="text-right">Received</TableHead>
                  <TableHead className="text-right">Remaining</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {order.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.product.name}</TableCell>
                    <TableCell className="text-right">{item.quantity}</TableCell>
                    <TableCell className="text-right">{item.rate.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{item.discount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{item.gstPercent}%</TableCell>
                    <TableCell className="text-right">{item.receivedQuantity}</TableCell>
                    <TableCell className="text-right">{item.remainingQuantity}</TableCell>
                    <TableCell className="text-right font-medium">{item.totalAmount.toFixed(2)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Separator className="my-4" />
          <div className="flex justify-end text-base font-semibold">
            <span>Grand Total: {order.totalAmount.toFixed(2)}</span>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={cancelOpen}
        title="Cancel Purchase Order"
        description={`Are you sure you want to cancel order ${order.orderNumber}? This cannot be undone.`}
        confirmLabel={cancelling ? 'Cancelling...' : 'Cancel Order'}
        cancelLabel="Keep Order"
        onConfirm={handleCancel}
        onCancel={() => setCancelOpen(false)}
      />
    </div>
  );
}
