import { Product } from '../types/product';
import { getStockStatus, stockStatusLabel, stockStatusVariant } from '../utils/stockStatus';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  show: boolean;
  product: Product | null;
  onClose: () => void;
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

export default function ProductViewModal({ show, product, onClose }: Props) {
  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Product Details</DialogTitle>
        </DialogHeader>

        {product && (
          <div className="space-y-5">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">{product.name}</h3>
              <Badge variant={product.status === 'ACTIVE' ? 'success' : 'muted'}>{product.status}</Badge>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Basic Info</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <InfoField label="SKU">{product.sku}</InfoField>
                <InfoField label="Barcode">{product.barcode || '—'}</InfoField>
                <InfoField label="Category">{product.categoryName}</InfoField>
                <InfoField label="Brand">{product.brand || '—'}</InfoField>
                <InfoField label="Unit">{product.unit}</InfoField>
                <InfoField label="Description">{product.description || '—'}</InfoField>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Pricing</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-3 gap-4">
                <InfoField label="Purchase Price">{product.purchasePrice.toFixed(2)}</InfoField>
                <InfoField label="Selling Price">{product.sellingPrice.toFixed(2)}</InfoField>
                <InfoField label="Tax">{product.tax.toFixed(2)}%</InfoField>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Inventory</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-3 gap-4">
                <InfoField label="Current Stock">{product.stockQuantity}</InfoField>
                <InfoField label="Minimum Stock Level">{product.minStockLevel}</InfoField>
                <InfoField label="Stock Status">
                  {(() => {
                    const status = getStockStatus(product);
                    return <Badge variant={stockStatusVariant(status)}>{stockStatusLabel(status)}</Badge>;
                  })()}
                </InfoField>
              </CardContent>
            </Card>

            <Separator />

            <div className="flex flex-col justify-between gap-1 text-xs text-muted-foreground sm:flex-row">
              <span>Created: {formatDate(product.createdAt)}</span>
              <span>Updated: {formatDate(product.updatedAt)}</span>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
