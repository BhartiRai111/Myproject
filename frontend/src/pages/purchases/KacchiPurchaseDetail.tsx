import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Pencil, Printer, Send, Trash2 } from 'lucide-react';
import { purchaseApi } from '../../api/purchaseApi';
import { parseApiError } from '../../utils/apiError';
import { Purchase, PurchaseStatus, PaymentStatus } from '../../types/purchase';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function paymentStatusVariant(status: PaymentStatus) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'destructive' as const;
}

function statusVariant(status: PurchaseStatus) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'DRAFT') return 'warning' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function KacchiPurchaseDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [purchase, setPurchase] = useState<Purchase | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [posting, setPosting] = useState(false);

  const load = () => {
    setLoading(true);
    purchaseApi
      .getById(Number(id))
      .then((res) => setPurchase(res.data))
      .catch((err) => toast.error(parseApiError(err, 'Failed to load Kacchi Purchase').message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [id]);

  const handlePost = async () => {
    setPosting(true);
    try {
      await purchaseApi.post(Number(id));
      toast.success('Kacchi Purchase posted successfully');
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to post challan').message);
    } finally {
      setPosting(false);
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await purchaseApi.remove(Number(id));
      toast.success('Kacchi Purchase deleted');
      navigate('/purchases/kacchi');
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete Kacchi Purchase').message);
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  if (loading || !purchase) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="print:hidden">
        <BackButton label="Back to Kacchi Purchases" onClick={() => navigate('/purchases/kacchi')} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between print:hidden">
        <PageHeader title={`Challan ${purchase.purchaseNumber}`} description={`Dated ${purchase.purchaseDate}`} />
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Print
          </Button>
          {purchase.status === 'DRAFT' && (
            <Button variant="outline" onClick={() => navigate(`/purchases/kacchi/${purchase.id}/edit`)}>
              <Pencil className="h-4 w-4" /> Edit
            </Button>
          )}
          {purchase.status === 'DRAFT' && (
            <Button loading={posting} onClick={handlePost}>
              <Send className="h-4 w-4" /> Post
            </Button>
          )}
          {purchase.status !== 'CANCELLED' && (
            <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          )}
        </div>
      </div>

      <Alert className="print:hidden">
        <AlertDescription>
          <strong>GST Calculated: YES</strong> &nbsp;·&nbsp; <strong>GST Reporting: NO</strong> — this challan's tax is fully
          calculated but excluded from GST return reporting (GSTR-1/GSTR-3B).
        </AlertDescription>
      </Alert>

      <Card className="print:border-0 print:shadow-none">
        <CardContent className="space-y-6 p-6">
          <div className="flex flex-col justify-between gap-4 sm:flex-row">
            <div>
              <h2 className="text-xl font-bold">StoreHub</h2>
              <p className="text-sm text-muted-foreground">Purchase Challan / Kacchi Purchase</p>
            </div>
            <div className="text-right text-sm">
              <p>
                <span className="text-muted-foreground">Challan No.: </span>
                <span className="font-medium">{purchase.purchaseNumber}</span>
              </p>
              <p>
                <span className="text-muted-foreground">Date: </span>
                {purchase.purchaseDate}
              </p>
              <div className="mt-1 flex justify-end gap-2">
                <Badge variant={statusVariant(purchase.status)}>{purchase.status}</Badge>
                <Badge variant="muted">GST Reporting: NO</Badge>
              </div>
            </div>
          </div>

          <Separator />

          <div className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2">
            <div>
              <p className="text-xs font-medium text-muted-foreground">Supplier / Place of Supply</p>
              <p className="font-medium">{purchase.supplier.name}</p>
              {purchase.supplierPhone && <p>{purchase.supplierPhone}</p>}
              {purchase.supplierGstin && <p>GSTIN: {purchase.supplierGstin}</p>}
              {purchase.billingAddress && <p className="whitespace-pre-line text-muted-foreground">{purchase.billingAddress}</p>}
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Ship To</p>
              <p className="whitespace-pre-line text-muted-foreground">{purchase.shippingAddress || '-'}</p>
            </div>
          </div>

          <div className="overflow-x-auto rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Item</TableHead>
                  <TableHead className="text-right">Qty</TableHead>
                  <TableHead className="text-right">Rate</TableHead>
                  <TableHead className="text-right">Discount</TableHead>
                  <TableHead className="text-right">Taxable</TableHead>
                  <TableHead className="text-right">CGST</TableHead>
                  <TableHead className="text-right">SGST</TableHead>
                  <TableHead className="text-right">IGST</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {purchase.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.product.name}</TableCell>
                    <TableCell className="text-right">{item.quantity}</TableCell>
                    <TableCell className="text-right">{item.purchasePrice.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{item.discount.toFixed(2)}</TableCell>
                    <TableCell className="text-right">{(item.taxableAmount ?? 0).toFixed(2)}</TableCell>
                    <TableCell className="text-right">{(item.cgstAmount ?? 0).toFixed(2)}</TableCell>
                    <TableCell className="text-right">{(item.sgstAmount ?? 0).toFixed(2)}</TableCell>
                    <TableCell className="text-right">{(item.igstAmount ?? 0).toFixed(2)}</TableCell>
                    <TableCell className="text-right font-medium">{item.subtotal.toFixed(2)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex justify-end">
            <div className="w-full max-w-xs space-y-1.5 text-sm">
              <div className="flex justify-between text-muted-foreground">
                <span>Taxable Amount</span>
                <span>{purchase.taxableAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>CGST</span>
                <span>{purchase.cgstAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>SGST</span>
                <span>{purchase.sgstAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>IGST</span>
                <span>{purchase.igstAmount.toFixed(2)}</span>
              </div>
              <Separator />
              <div className="flex justify-between text-base font-semibold">
                <span>Grand Total</span>
                <span>{purchase.totalAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>Paid Amount</span>
                <span>{purchase.paidAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>Due Amount</span>
                <span>{purchase.payableAmount.toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>Payment Mode</span>
                <span>{purchase.paymentMode}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Payment Status</span>
                <Badge variant={paymentStatusVariant(purchase.paymentStatus)}>{purchase.paymentStatus}</Badge>
              </div>
            </div>
          </div>

          {purchase.notes && (
            <div className="text-sm">
              <p className="text-xs font-medium text-muted-foreground">Remarks</p>
              <p>{purchase.notes}</p>
            </div>
          )}

          <p className="text-xs text-muted-foreground">
            This is a Purchase Challan (Kacchi Purchase). Tax is calculated in full but this document is excluded from GST
            return reporting.
          </p>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        title="Delete Kacchi Purchase"
        description={
          purchase.status === 'DRAFT'
            ? `Deleting draft challan ${purchase.purchaseNumber} will remove it. It was never posted, so nothing needs to be reversed.`
            : `Deleting challan ${purchase.purchaseNumber} will restore its stock, reverse the supplier account entry, and reverse the accounting journal. This cannot be undone.`
        }
        confirmLabel={deleting ? 'Deleting...' : 'Delete'}
        cancelLabel="Keep"
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </div>
  );
}
