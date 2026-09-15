import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Search } from 'lucide-react';
import { purchaseApi } from '../../api/purchaseApi';
import { debitNoteApi } from '../../api/debitNoteApi';
import { parseApiError } from '../../utils/apiError';
import { Purchase } from '../../types/purchase';
import { DebitNoteType, StockImpactType } from '../../types/note';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const NOTE_TYPES: DebitNoteType[] = ['PURCHASE_RETURN', 'SUPPLIER_DEBIT', 'PRICE_ADJUSTMENT', 'TAX_ADJUSTMENT'];

export default function DebitNoteForm() {
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [searching, setSearching] = useState(false);
  const [candidates, setCandidates] = useState<Purchase[]>([]);
  const [purchase, setPurchase] = useState<Purchase | null>(null);

  const [noteType, setNoteType] = useState<DebitNoteType>('PURCHASE_RETURN');
  const [noteDate, setNoteDate] = useState(new Date().toISOString().slice(0, 10));
  const [stockImpact, setStockImpact] = useState<StockImpactType>('STOCK_RETURN');
  const [reason, setReason] = useState('');
  const [remarks, setRemarks] = useState('');
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setSearching(true);
    try {
      const res = await purchaseApi.list({ search, status: 'COMPLETED', page: 0, size: 10 });
      setCandidates(res.data.content);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to search purchases').message);
    } finally {
      setSearching(false);
    }
  };

  const selectPurchase = (p: Purchase) => {
    setPurchase(p);
    setCandidates([]);
    setQuantities({});
  };

  const setQty = (purchaseItemId: number, qty: number) => {
    setQuantities((prev) => ({ ...prev, [purchaseItemId]: qty }));
  };

  const handleSubmit = async (post: boolean) => {
    if (!purchase) return;
    const items = Object.entries(quantities)
      .filter(([, qty]) => qty > 0)
      .map(([purchaseItemId, quantity]) => ({ purchaseItemId: Number(purchaseItemId), quantity }));

    if (items.length === 0) {
      setError('Enter a return quantity for at least one item');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      const res = await debitNoteApi.create({
        sourcePurchaseId: purchase.id,
        noteType,
        noteDate,
        stockImpact,
        reason: reason || undefined,
        remarks: remarks || undefined,
        items,
        post,
      });
      toast.success(`Debit Note ${res.data.voucherNumber} ${post ? 'posted' : 'saved as draft'}`);
      navigate(`/purchases/debit-notes/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to create debit note').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Debit Notes" onClick={() => navigate('/purchases/debit-notes')} />

      <PageHeader title="New Debit Note" description="Create a debit note against a posted purchase." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {!purchase ? (
        <Card>
          <CardContent className="space-y-4 p-5">
            <Label>Find the source purchase</Label>
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder="Search by purchase number or supplier"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9"
                />
              </div>
              <Button type="submit" loading={searching}>
                Search
              </Button>
            </form>

            {candidates.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Purchase No.</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Supplier</TableHead>
                    <TableHead>Total</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {candidates.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell className="font-mono text-sm">{p.purchaseNumber}</TableCell>
                      <TableCell>{p.purchaseDate}</TableCell>
                      <TableCell>{p.supplier?.name || '—'}</TableCell>
                      <TableCell>{money(p.totalAmount)}</TableCell>
                      <TableCell className="text-right">
                        <Button size="sm" variant="outline" onClick={() => selectPurchase(p)} disabled={!p.supplier}>
                          {p.supplier ? 'Select' : 'No supplier'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardContent className="flex items-center justify-between p-5">
              <div>
                <p className="text-sm text-muted-foreground">Source Purchase</p>
                <p className="font-semibold">{purchase.purchaseNumber} — {purchase.supplier?.name}</p>
              </div>
              <Button variant="outline" size="sm" onClick={() => setPurchase(null)}>
                Change
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-4 p-5">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label>Note Type</Label>
                  <Select value={noteType} onValueChange={(v) => setNoteType(v as DebitNoteType)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {NOTE_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>
                          {t.replace('_', ' ')}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label>Note Date</Label>
                  <Input type="date" value={noteDate} onChange={(e) => setNoteDate(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label>Stock Impact</Label>
                  <Select value={stockImpact} onValueChange={(v) => setStockImpact(v as StockImpactType)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="STOCK_RETURN">Stock Return (decreases stock)</SelectItem>
                      <SelectItem value="FINANCIAL_ADJUSTMENT">Financial Adjustment (no stock change)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Reason</Label>
                <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Defective goods returned to supplier" />
              </div>
              <div className="space-y-1.5">
                <Label>Remarks</Label>
                <Textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>Purchased Qty</TableHead>
                    <TableHead>Rate</TableHead>
                    <TableHead>Return Qty</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {purchase.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.product.name}</TableCell>
                      <TableCell>{item.quantity}</TableCell>
                      <TableCell>{money(item.purchasePrice)}</TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          max={item.quantity}
                          className="w-24"
                          value={quantities[item.id] ?? ''}
                          onChange={(e) => setQty(item.id, Number(e.target.value))}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <div className="flex justify-end gap-2">
            <Button variant="outline" loading={submitting} onClick={() => handleSubmit(false)}>
              Save as Draft
            </Button>
            <Button loading={submitting} onClick={() => handleSubmit(true)}>
              Save and Post
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
