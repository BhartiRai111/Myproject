import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Printer, Trash2 } from 'lucide-react';
import { paymentApi } from '../../api/paymentApi';
import { parseApiError } from '../../utils/apiError';
import { Payment } from '../../types/payment';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

export default function PaymentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    setLoading(true);
    paymentApi
      .getById(Number(id))
      .then((res) => setPayment(res.data))
      .catch((err) => toast.error(parseApiError(err, 'Failed to load payment').message))
      .finally(() => setLoading(false));
  }, [id]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await paymentApi.remove(Number(id));
      toast.success('Payment deleted and reversed');
      navigate('/purchases/payments');
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete payment').message);
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  if (loading || !payment) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const supplierName = payment.supplier ? payment.supplier.name : '-';

  return (
    <div className="space-y-6">
      <div className="print:hidden">
        <BackButton label="Back to Payments" onClick={() => navigate('/purchases/payments')} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between print:hidden">
        <PageHeader title={`Payment ${payment.paymentNumber}`} description={`Dated ${payment.paymentDate}`} />
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Print
          </Button>
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            <Trash2 className="h-4 w-4" /> Delete
          </Button>
        </div>
      </div>

      <Card className="print:border-0 print:shadow-none">
        <CardContent className="space-y-6 p-6">
          <div className="flex flex-col justify-between gap-4 sm:flex-row">
            <div>
              <h2 className="text-xl font-bold">StoreHub</h2>
              <p className="text-sm text-muted-foreground">Payment Voucher</p>
            </div>
            <div className="text-right text-sm">
              <p>
                <span className="text-muted-foreground">Payment No.: </span>
                <span className="font-medium">{payment.paymentNumber}</span>
              </p>
              <p>
                <span className="text-muted-foreground">Date: </span>
                {payment.paymentDate}
              </p>
            </div>
          </div>

          <Separator />

          <div className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-3">
            <div>
              <p className="text-xs font-medium text-muted-foreground">Paid To</p>
              <p className="font-medium">{supplierName}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Payment Mode</p>
              <p>{payment.paymentMode}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Amount Paid</p>
              <p className="text-lg font-semibold">{payment.amount.toFixed(2)}</p>
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-medium text-muted-foreground">Applied Against</p>
            {payment.allocations.length === 0 ? (
              <p className="text-sm text-muted-foreground">Recorded as an on-account credit (not tied to a specific bill).</p>
            ) : (
              <div className="overflow-hidden rounded-lg border border-border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Bill</TableHead>
                      <TableHead className="text-right">Amount Applied</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {payment.allocations.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell className="font-medium">{a.purchaseNumber}</TableCell>
                        <TableCell className="text-right">{a.amountApplied.toFixed(2)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>

          {payment.remarks && (
            <div className="text-sm">
              <p className="text-xs font-medium text-muted-foreground">Remarks</p>
              <p>{payment.remarks}</p>
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        title="Delete Payment"
        description={`Deleting payment ${payment.paymentNumber} will restore the payable amount on its linked bill(s) and reverse the cash/bank entry. This cannot be undone.`}
        confirmLabel={deleting ? 'Deleting...' : 'Delete Payment'}
        cancelLabel="Keep Payment"
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </div>
  );
}
