import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Printer, Trash2 } from 'lucide-react';
import { receiptApi } from '../../api/receiptApi';
import { parseApiError } from '../../utils/apiError';
import { Receipt } from '../../types/receipt';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

export default function ReceiptDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    setLoading(true);
    receiptApi
      .getById(Number(id))
      .then((res) => setReceipt(res.data))
      .catch((err) => toast.error(parseApiError(err, 'Failed to load receipt').message))
      .finally(() => setLoading(false));
  }, [id]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await receiptApi.remove(Number(id));
      toast.success('Receipt deleted and reversed');
      navigate('/sales/receipts');
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to delete receipt').message);
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  if (loading || !receipt) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const customerName = receipt.customer ? `${receipt.customer.firstName} ${receipt.customer.lastName || ''}` : '-';

  return (
    <div className="space-y-6">
      <div className="print:hidden">
        <BackButton label="Back to Receipts" onClick={() => navigate('/sales/receipts')} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between print:hidden">
        <PageHeader title={`Receipt ${receipt.receiptNumber}`} description={`Dated ${receipt.receiptDate}`} />
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
              <p className="text-sm text-muted-foreground">Payment Receipt</p>
            </div>
            <div className="text-right text-sm">
              <p>
                <span className="text-muted-foreground">Receipt No.: </span>
                <span className="font-medium">{receipt.receiptNumber}</span>
              </p>
              <p>
                <span className="text-muted-foreground">Date: </span>
                {receipt.receiptDate}
              </p>
            </div>
          </div>

          <Separator />

          <div className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-3">
            <div>
              <p className="text-xs font-medium text-muted-foreground">Received From</p>
              <p className="font-medium">{customerName}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Payment Mode</p>
              <p>{receipt.paymentMode}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Amount Received</p>
              <p className="text-lg font-semibold">{receipt.amount.toFixed(2)}</p>
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-medium text-muted-foreground">Applied Against</p>
            {receipt.allocations.length === 0 ? (
              <p className="text-sm text-muted-foreground">Recorded as an on-account credit (not tied to a specific invoice).</p>
            ) : (
              <div className="overflow-hidden rounded-lg border border-border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Invoice</TableHead>
                      <TableHead className="text-right">Amount Applied</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {receipt.allocations.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell className="font-medium">{a.invoiceNumber}</TableCell>
                        <TableCell className="text-right">{a.amountApplied.toFixed(2)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>

          {receipt.remarks && (
            <div className="text-sm">
              <p className="text-xs font-medium text-muted-foreground">Remarks</p>
              <p>{receipt.remarks}</p>
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        title="Delete Receipt"
        description={`Deleting receipt ${receipt.receiptNumber} will restore the due amount on its linked bill(s) and reverse the cash/bank entry. This cannot be undone.`}
        confirmLabel={deleting ? 'Deleting...' : 'Delete Receipt'}
        cancelLabel="Keep Receipt"
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </div>
  );
}
