import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Printer } from 'lucide-react';
import { debitNoteApi } from '../../api/debitNoteApi';
import { parseApiError } from '../../utils/apiError';
import { DebitNote } from '../../types/note';
import { useAuth } from '@/context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function statusVariant(status: string) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function DebitNoteDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [note, setNote] = useState<DebitNote | null>(null);
  const [loading, setLoading] = useState(true);
  const [posting, setPosting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [postConfirm, setPostConfirm] = useState(false);
  const [cancelConfirm, setCancelConfirm] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await debitNoteApi.getById(Number(id));
      setNote(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load debit note').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handlePost = async () => {
    if (!note) return;
    setPosting(true);
    try {
      await debitNoteApi.post(note.id);
      toast.success(`Debit Note ${note.voucherNumber} posted`);
      setPostConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to post debit note').message);
      setPostConfirm(false);
    } finally {
      setPosting(false);
    }
  };

  const handleCancel = async () => {
    if (!note) return;
    setCancelling(true);
    try {
      await debitNoteApi.cancel(note.id);
      toast.success(`Debit Note ${note.voucherNumber} cancelled`);
      setCancelConfirm(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to cancel debit note').message);
      setCancelConfirm(false);
    } finally {
      setCancelling(false);
    }
  };

  if (loading || !note) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6 print:space-y-3">
      <div className="print:hidden">
        <BackButton label="Back to Debit Notes" onClick={() => navigate('/purchases/debit-notes')} />
      </div>

      <PageHeader
        title={note.voucherNumber}
        description={`Debit Note against Purchase ${note.sourcePurchaseNumber}`}
        actions={
          <div className="flex gap-2 print:hidden">
            <Button variant="outline" onClick={() => window.print()}>
              <Printer className="h-4 w-4" /> Print
            </Button>
            {canManage && note.status === 'DRAFT' && (
              <Button onClick={() => setPostConfirm(true)}>Post</Button>
            )}
            {canManage && note.status !== 'CANCELLED' && (
              <Button variant="destructive" onClick={() => setCancelConfirm(true)}>
                Cancel
              </Button>
            )}
          </div>
        }
      />

      <Card className="print:border-0 print:shadow-none">
        <CardContent className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">Status</p>
            <Badge variant={statusVariant(note.status)}>{note.status}</Badge>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Note Date</p>
            <p className="font-medium">{note.noteDate}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Note Type</p>
            <p className="font-medium">{note.noteType.replace('_', ' ')}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Stock Impact</p>
            <p className="font-medium">{note.stockImpact.replace('_', ' ')}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Supplier</p>
            <p className="font-medium">{note.supplier.name}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">GST Reporting</p>
            <p className="font-medium">{note.gstReportingApplicable ? 'Reportable' : 'Non-Reportable (Kacchi)'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Reason</p>
            <p className="font-medium">{note.reason || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Posted By</p>
            <p className="font-medium">{note.postedBy || '—'}</p>
          </div>
        </CardContent>
      </Card>

      <Card className="print:border-0 print:shadow-none">
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Product</TableHead>
                <TableHead>Qty</TableHead>
                <TableHead>Rate</TableHead>
                <TableHead>Taxable</TableHead>
                <TableHead>CGST</TableHead>
                <TableHead>SGST</TableHead>
                <TableHead>IGST</TableHead>
                <TableHead className="text-right">Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {note.items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-medium">{item.productName}</TableCell>
                  <TableCell>{item.quantity}</TableCell>
                  <TableCell>{money(item.rate)}</TableCell>
                  <TableCell>{money(item.taxableAmount)}</TableCell>
                  <TableCell>{money(item.cgstAmount || 0)}</TableCell>
                  <TableCell>{money(item.sgstAmount || 0)}</TableCell>
                  <TableCell>{money(item.igstAmount || 0)}</TableCell>
                  <TableCell className="text-right font-medium">{money(item.total)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card className="print:border-0 print:shadow-none">
        <CardContent className="flex flex-col items-end gap-1 p-5 text-sm">
          <div className="flex w-full max-w-xs justify-between">
            <span className="text-muted-foreground">Taxable Amount</span>
            <span>{money(note.taxableAmount)}</span>
          </div>
          <div className="flex w-full max-w-xs justify-between">
            <span className="text-muted-foreground">Total Tax</span>
            <span>{money(note.totalTax)}</span>
          </div>
          <div className="flex w-full max-w-xs justify-between text-base font-semibold">
            <span>Total Amount</span>
            <span>{money(note.totalAmount)}</span>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={postConfirm}
        title={`Post Debit Note ${note.voucherNumber}?`}
        description="This will post the accounting journal, update stock (if applicable), and record the supplier ledger entry. This cannot be undone directly — only via cancellation."
        confirmLabel={posting ? 'Posting...' : 'Post'}
        destructive={false}
        onConfirm={handlePost}
        onCancel={() => setPostConfirm(false)}
      />

      <ConfirmDialog
        open={cancelConfirm}
        title={`Cancel Debit Note ${note.voucherNumber}?`}
        description="This will reverse stock, ledger, accounting and applicable GST effects if this note was posted."
        confirmLabel={cancelling ? 'Cancelling...' : 'Cancel Note'}
        cancelLabel="Keep Note"
        onConfirm={handleCancel}
        onCancel={() => setCancelConfirm(false)}
      />
    </div>
  );
}
