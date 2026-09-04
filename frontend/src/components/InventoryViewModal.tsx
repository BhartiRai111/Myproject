import { Inventory, StockStatus } from '../types/inventory';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  show: boolean;
  inventory: Inventory | null;
  onClose: () => void;
}

function stockStatusVariant(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'destructive' as const;
  if (status === 'LOW_STOCK') return 'warning' as const;
  return 'success' as const;
}

function stockStatusLabel(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'Out of Stock';
  if (status === 'LOW_STOCK') return 'Low Stock';
  return 'In Stock';
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function InfoField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-0.5 text-sm font-medium">{children}</div>
    </div>
  );
}

export default function InventoryViewModal({ show, inventory, onClose }: Props) {
  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Inventory Details</DialogTitle>
        </DialogHeader>

        {inventory && (
          <div className="space-y-5">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">{inventory.productName}</h3>
              <Badge variant={stockStatusVariant(inventory.stockStatus)}>{stockStatusLabel(inventory.stockStatus)}</Badge>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Product Information</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="SKU">{inventory.sku || '—'}</InfoField>
                <InfoField label="Category">{inventory.categoryName || '—'}</InfoField>
                <InfoField label="Unit">{inventory.unit}</InfoField>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Stock Information</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="Current Stock">{inventory.currentStock}</InfoField>
                <InfoField label="Minimum Stock Level">{inventory.minStockLevel}</InfoField>
                {inventory.maxStockLevel != null && (
                  <InfoField label="Maximum Stock Level">{inventory.maxStockLevel}</InfoField>
                )}
                <InfoField label="Stock Status">
                  <Badge variant={stockStatusVariant(inventory.stockStatus)}>{stockStatusLabel(inventory.stockStatus)}</Badge>
                </InfoField>
                <InfoField label="Last Updated">{formatDate(inventory.lastUpdated)}</InfoField>
              </CardContent>
            </Card>

            <Separator />

            <div className="flex flex-col justify-between gap-1 text-xs text-muted-foreground sm:flex-row">
              <span>Created: {formatDate(inventory.createdAt)}</span>
              <span>Updated: {formatDate(inventory.updatedAt)}</span>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
