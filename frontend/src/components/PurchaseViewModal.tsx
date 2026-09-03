import { Purchase } from '../types/purchase';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface Props {
  show: boolean;
  purchase: Purchase | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function formatMoney(value: number) {
  return value.toFixed(2);
}

function paymentStatusVariant(status: Purchase['paymentStatus']) {
  if (status === 'PAID') return 'success' as const;
  if (status === 'PARTIAL') return 'warning' as const;
  return 'destructive' as const;
}

function purchaseStatusVariant(status: Purchase['status']) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

function InfoField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-sm font-medium">{children}</p>
    </div>
  );
}

export default function PurchaseViewModal({ show, purchase, onClose }: Props) {
  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Purchase Details</DialogTitle>
        </DialogHeader>

        {purchase && (
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <InfoField label="Purchase Number">{purchase.purchaseNumber}</InfoField>
              <InfoField label="Supplier">{purchase.supplier.name}</InfoField>
              <InfoField label="Purchase Date">{purchase.purchaseDate}</InfoField>
              <InfoField label="Payment Status">
                <Badge variant={paymentStatusVariant(purchase.paymentStatus)}>{purchase.paymentStatus}</Badge>
              </InfoField>
              <InfoField label="Purchase Status">
                <Badge variant={purchaseStatusVariant(purchase.status)}>{purchase.status}</Badge>
              </InfoField>
              <InfoField label="Notes">{purchase.notes || '—'}</InfoField>
            </div>

            <Separator />

            <div className="overflow-hidden rounded-lg border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead className="text-right">Qty</TableHead>
                    <TableHead className="text-right">Price</TableHead>
                    <TableHead className="text-right">Discount</TableHead>
                    <TableHead className="text-right">Tax</TableHead>
                    <TableHead className="text-right">Subtotal</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {purchase.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        {item.product.name}
                        {item.product.unit && <span className="text-muted-foreground"> ({item.product.unit})</span>}
                      </TableCell>
                      <TableCell className="text-right">{item.quantity}</TableCell>
                      <TableCell className="text-right">{formatMoney(item.purchasePrice)}</TableCell>
                      <TableCell className="text-right">{formatMoney(item.discount)}</TableCell>
                      <TableCell className="text-right">{formatMoney(item.tax)}</TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(item.subtotal)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="flex justify-end">
              <div className="w-full max-w-xs space-y-1.5 text-sm">
                <div className="flex justify-between text-muted-foreground">
                  <span>Subtotal</span>
                  <span>{formatMoney(purchase.subtotalAmount)}</span>
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>Total Discount</span>
                  <span>-{formatMoney(purchase.totalDiscount)}</span>
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>Total Tax</span>
                  <span>+{formatMoney(purchase.totalTax)}</span>
                </div>
                <Separator />
                <div className="flex justify-between text-base font-semibold">
                  <span>Grand Total</span>
                  <span>{formatMoney(purchase.totalAmount)}</span>
                </div>
              </div>
            </div>

            <Separator />

            <div className="flex flex-col justify-between gap-1 text-xs text-muted-foreground sm:flex-row">
              <span>Created: {formatDate(purchase.createdAt)}</span>
              <span>Updated: {formatDate(purchase.updatedAt)}</span>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
