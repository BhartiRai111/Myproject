import { useEffect, useState } from 'react';
import { supplierApi } from '../api/supplierApi';
import { parseApiError } from '../utils/apiError';
import { Supplier, SupplierPurchaseSummary } from '../types/supplier';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface Props {
  show: boolean;
  supplier: Supplier | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function paymentStatusVariant(status: SupplierPurchaseSummary['paymentStatus']) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'destructive' as const;
}

function purchaseStatusVariant(status: SupplierPurchaseSummary['status']) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

function InfoField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-0.5 text-sm font-medium">{children}</div>
    </div>
  );
}

export default function SupplierViewModal({ show, supplier, onClose }: Props) {
  const [history, setHistory] = useState<SupplierPurchaseSummary[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  useEffect(() => {
    if (show && supplier) {
      setLoadingHistory(true);
      supplierApi
        .getPurchaseHistory(supplier.id)
        .then((res) => setHistory(res.data))
        .catch((err) => {
          console.error(parseApiError(err, 'Failed to load purchase history').message);
          setHistory([]);
        })
        .finally(() => setLoadingHistory(false));
    }
  }, [show, supplier]);

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Supplier Details</DialogTitle>
        </DialogHeader>

        {supplier && (
          <div className="space-y-5">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">{supplier.name}</h3>
              <Badge variant={supplier.status === 'ACTIVE' ? 'success' : 'muted'}>{supplier.status}</Badge>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Basic Information</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="Contact Person">{supplier.contactPerson || '—'}</InfoField>
                <InfoField label="Mobile">{supplier.mobile}</InfoField>
                <InfoField label="Email">{supplier.email || '—'}</InfoField>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Address</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="Address">{supplier.address || '—'}</InfoField>
                <InfoField label="City">{supplier.city || '—'}</InfoField>
                <InfoField label="State">{supplier.state || '—'}</InfoField>
                <InfoField label="Pincode">{supplier.pincode || '—'}</InfoField>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Business</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="GST Number">{supplier.gstNumber || '—'}</InfoField>
              </CardContent>
            </Card>

            {supplier.notes && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">Notes</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm">{supplier.notes}</p>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Purchase History</CardTitle>
              </CardHeader>
              <CardContent>
                {loadingHistory ? (
                  <div className="space-y-2">
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-full" />
                  </div>
                ) : history.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted-foreground">No purchases recorded yet.</p>
                ) : (
                  <div className="overflow-hidden rounded-lg border border-border">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Purchase #</TableHead>
                          <TableHead>Date</TableHead>
                          <TableHead className="text-right">Total</TableHead>
                          <TableHead>Payment</TableHead>
                          <TableHead>Status</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {history.map((h) => (
                          <TableRow key={h.id}>
                            <TableCell className="font-medium">{h.purchaseNumber}</TableCell>
                            <TableCell className="text-muted-foreground">{h.purchaseDate}</TableCell>
                            <TableCell className="text-right">{h.totalAmount.toFixed(2)}</TableCell>
                            <TableCell>
                              <Badge variant={paymentStatusVariant(h.paymentStatus)}>{h.paymentStatus}</Badge>
                            </TableCell>
                            <TableCell>
                              <Badge variant={purchaseStatusVariant(h.status)}>{h.status}</Badge>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </CardContent>
            </Card>

            <Separator />

            <div className="flex flex-col justify-between gap-1 text-xs text-muted-foreground sm:flex-row">
              <span>Created: {formatDate(supplier.createdAt)}</span>
              <span>Updated: {formatDate(supplier.updatedAt)}</span>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
