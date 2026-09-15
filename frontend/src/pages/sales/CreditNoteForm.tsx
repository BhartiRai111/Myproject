import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Search } from 'lucide-react';
import { saleApi } from '../../api/saleApi';
import { creditNoteApi } from '../../api/creditNoteApi';
import { parseApiError } from '../../utils/apiError';
import { Sale } from '../../types/sale';
import { CreditNoteType, StockImpactType } from '../../types/note';
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

const NOTE_TYPES: CreditNoteType[] = ['SALES_RETURN', 'PRICE_ADJUSTMENT', 'DISCOUNT_ADJUSTMENT', 'TAX_ADJUSTMENT', 'CUSTOMER_CREDIT'];

export default function CreditNoteForm() {
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [searching, setSearching] = useState(false);
  const [candidates, setCandidates] = useState<Sale[]>([]);
  const [sale, setSale] = useState<Sale | null>(null);

  const [noteType, setNoteType] = useState<CreditNoteType>('SALES_RETURN');
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
      const res = await saleApi.list({ search, status: 'COMPLETED', page: 0, size: 10 });
      setCandidates(res.data.content);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to search sales').message);
    } finally {
      setSearching(false);
    }
  };

  const selectSale = (s: Sale) => {
    setSale(s);
    setCandidates([]);
    setQuantities({});
  };

  const setQty = (saleItemId: number, qty: number) => {
    setQuantities((p) => ({ ...p, [saleItemId]: qty }));
  };

  const handleSubmit = async (post: boolean) => {
    if (!sale) return;
    const items = Object.entries(quantities)
      .filter(([, qty]) => qty > 0)
      .map(([saleItemId, quantity]) => ({ saleItemId: Number(saleItemId), quantity }));

    if (items.length === 0) {
      setError('Enter a return quantity for at least one item');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      const res = await creditNoteApi.create({
        sourceSaleId: sale.id,
        noteType,
        noteDate,
        stockImpact,
        reason: reason || undefined,
        remarks: remarks || undefined,
        items,
        post,
      });
      toast.success(`Credit Note ${res.data.voucherNumber} ${post ? 'posted' : 'saved as draft'}`);
      navigate(`/sales/credit-notes/${res.data.id}`);
    } catch (err) {
      setError(parseApiError(err, 'Failed to create credit note').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Credit Notes" onClick={() => navigate('/sales/credit-notes')} />

      <PageHeader title="New Credit Note" description="Create a credit note against a posted sale." />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {!sale ? (
        <Card>
          <CardContent className="space-y-4 p-5">
            <Label>Find the source sale</Label>
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder="Search by invoice number or customer"
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
                    <TableHead>Invoice</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Total</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {candidates.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-mono text-sm">{s.invoiceNumber}</TableCell>
                      <TableCell>{s.saleDate}</TableCell>
                      <TableCell>{s.customer ? `${s.customer.firstName} ${s.customer.lastName || ''}` : 'Walk-in'}</TableCell>
                      <TableCell>{money(s.totalAmount)}</TableCell>
                      <TableCell className="text-right">
                        <Button size="sm" variant="outline" onClick={() => selectSale(s)} disabled={!s.customer}>
                          {s.customer ? 'Select' : 'No customer'}
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
                <p className="text-sm text-muted-foreground">Source Sale</p>
                <p className="font-semibold">{sale.invoiceNumber} — {sale.customer?.firstName} {sale.customer?.lastName}</p>
              </div>
              <Button variant="outline" size="sm" onClick={() => setSale(null)}>
                Change
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-4 p-5">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label>Note Type</Label>
                  <Select value={noteType} onValueChange={(v) => setNoteType(v as CreditNoteType)}>
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
                      <SelectItem value="STOCK_RETURN">Stock Return (increases stock)</SelectItem>
                      <SelectItem value="FINANCIAL_ADJUSTMENT">Financial Adjustment (no stock change)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Reason</Label>
                <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Damaged goods returned" />
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
                    <TableHead>Sold Qty</TableHead>
                    <TableHead>Rate</TableHead>
                    <TableHead>Return Qty</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sale.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.product.name}</TableCell>
                      <TableCell>{item.quantity}</TableCell>
                      <TableCell>{money(item.sellingPrice)}</TableCell>
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
