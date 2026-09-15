import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Loader2, Minus, Plus, Printer, Scan, Trash2, X } from 'lucide-react';
import { productApi } from '@/api/productApi';
import { customerApi } from '@/api/customerApi';
import { saleApi } from '@/api/saleApi';
import { parseApiError } from '@/utils/apiError';
import { Product } from '@/types/product';
import { Customer, PaymentMode, Sale, SaleCreatePayload } from '@/types/sale';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const round2 = (n: number) => Math.round(n * 100) / 100;

interface CartLine {
  productId: number;
  name: string;
  sku: string;
  barcode?: string;
  qty: number;
  rate: number;
  discount: number;
  gstPercent: number;
  stockQuantity: number;
}

function newClientRequestId() {
  return `pos-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

type PostState = 'idle' | 'saving' | 'posted' | 'failed';

export default function Pos() {
  const navigate = useNavigate();
  const searchRef = useRef<HTMLInputElement>(null);
  const clientRequestIdRef = useRef(newClientRequestId());
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Product[]>([]);
  const [searching, setSearching] = useState(false);
  const [showResults, setShowResults] = useState(false);

  const [cart, setCart] = useState<CartLine[]>([]);

  const [customerQuery, setCustomerQuery] = useState('');
  const [customerResults, setCustomerResults] = useState<Customer[]>([]);
  const [customer, setCustomer] = useState<Customer | null>(null);

  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [paidAmount, setPaidAmount] = useState('');
  const [taxMode, setTaxMode] = useState<'INTRA_STATE' | 'INTER_STATE'>('INTRA_STATE');

  const [postState, setPostState] = useState<PostState>('idle');
  const [error, setError] = useState('');
  const [postedSale, setPostedSale] = useState<Sale | null>(null);

  useEffect(() => {
    searchRef.current?.focus();
  }, []);

  // ---- product search (debounced live search; Enter fast-adds an exact barcode/SKU match for scanners) ----
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const q = query.trim();
    if (q.length < 1) {
      setResults([]);
      return;
    }
    setSearching(true);
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await productApi.list({ search: q, status: 'ACTIVE', size: 8 });
        setResults(res.data.content);
        setShowResults(true);
      } catch {
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 200);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  const addToCart = (product: Product) => {
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === product.id);
      if (existing) {
        return prev.map((l) => (l.productId === product.id ? { ...l, qty: l.qty + 1 } : l));
      }
      return [
        ...prev,
        {
          productId: product.id,
          name: product.name,
          sku: product.sku,
          barcode: product.barcode,
          qty: 1,
          rate: product.sellingPrice,
          discount: 0,
          gstPercent: product.tax || 0,
          stockQuantity: product.stockQuantity,
        },
      ];
    });
    setQuery('');
    setResults([]);
    setShowResults(false);
    searchRef.current?.focus();
  };

  const handleSearchKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    try {
      const res = await productApi.list({ search: q, status: 'ACTIVE', size: 8 });
      const items = res.data.content;
      const exact = items.find((p) => p.barcode?.toLowerCase() === q.toLowerCase() || p.sku.toLowerCase() === q.toLowerCase());
      if (exact) {
        addToCart(exact);
      } else if (items.length === 1) {
        addToCart(items[0]);
      } else {
        setResults(items);
        setShowResults(true);
      }
    } catch (err) {
      toast.error(parseApiError(err, 'Product search failed').message);
    }
  };

  const updateLine = (productId: number, patch: Partial<CartLine>) => {
    setCart((prev) => prev.map((l) => (l.productId === productId ? { ...l, ...patch } : l)));
  };

  const removeLine = (productId: number) => {
    setCart((prev) => prev.filter((l) => l.productId !== productId));
  };

  // ---- customer search ----
  useEffect(() => {
    if (customerQuery.trim().length < 1) {
      setCustomerResults([]);
      return;
    }
    const t = setTimeout(async () => {
      try {
        const res = await customerApi.list();
        const q = customerQuery.trim().toLowerCase();
        setCustomerResults(
          res.data.filter(
            (c) => `${c.firstName} ${c.lastName || ''}`.toLowerCase().includes(q) || c.mobile.includes(q)
          ).slice(0, 8)
        );
      } catch {
        setCustomerResults([]);
      }
    }, 200);
    return () => clearTimeout(t);
  }, [customerQuery]);

  // ---- totals (client-side preview only; SaleService computes the authoritative amounts) ----
  const lineTotals = useMemo(
    () =>
      cart.map((l) => {
        const taxable = round2(l.qty * l.rate - l.discount);
        const gst = round2((taxable * l.gstPercent) / 100);
        return { ...l, taxable, gst, total: round2(taxable + gst) };
      }),
    [cart]
  );

  const isGst = cart.some((l) => l.gstPercent > 0);
  const subtotal = round2(lineTotals.reduce((s, l) => s + l.taxable, 0));
  const totalDiscount = round2(cart.reduce((s, l) => s + l.discount, 0));
  const totalTax = round2(lineTotals.reduce((s, l) => s + l.gst, 0));
  const grandTotal = round2(subtotal + totalTax);
  const cgst = isGst && taxMode === 'INTRA_STATE' ? round2(totalTax / 2) : 0;
  const sgst = isGst && taxMode === 'INTRA_STATE' ? round2(totalTax - cgst) : 0;
  const igst = isGst && taxMode === 'INTER_STATE' ? totalTax : 0;

  useEffect(() => {
    setPaidAmount(grandTotal > 0 ? String(grandTotal) : '');
  }, [grandTotal]);

  const clearCart = () => {
    setCart([]);
    setCustomer(null);
    setCustomerQuery('');
    setPaymentMode('CASH');
    setError('');
    setPostState('idle');
    setPostedSale(null);
    clientRequestIdRef.current = newClientRequestId();
    searchRef.current?.focus();
  };

  const handlePost = async () => {
    if (cart.length === 0) {
      setError('Cart is empty');
      return;
    }
    const paid = Number(paidAmount) || 0;
    if (paid < grandTotal && !customer) {
      setError('A customer must be selected for a credit sale (paid amount is less than the total)');
      return;
    }
    if (isGst && !taxMode) {
      setError('Tax mode is required for a GST sale');
      return;
    }

    setError('');
    setPostState('saving');
    try {
      const payload: SaleCreatePayload = {
        clientRequestId: clientRequestIdRef.current,
        customerId: customer?.id ?? null,
        saleDate: new Date().toISOString().slice(0, 10),
        gstType: isGst ? 'GST' : 'NON_GST',
        taxMode: isGst ? taxMode : undefined,
        paymentMode,
        paidAmount: paid,
        transactionType: 'SALE',
        items: cart.map((l) => ({
          productId: l.productId,
          quantity: l.qty,
          sellingPrice: l.rate,
          discount: l.discount,
          tax: 0,
          gstPercent: l.gstPercent,
        })),
      };
      const res = await saleApi.create(payload);
      setPostedSale(res.data);
      setPostState('posted');
      toast.success(`Sale ${res.data.invoiceNumber} posted`);
    } catch (err) {
      setPostState('failed');
      setError(parseApiError(err, 'Failed to post sale — nothing was charged. You can safely retry.').message);
    }
  };

  if (postedSale) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between print:hidden">
          <PageHeader title="Sale Posted" description={`Invoice ${postedSale.invoiceNumber}`} />
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => window.print()}>
              <Printer className="h-4 w-4" /> Print Receipt
            </Button>
            <Button onClick={clearCart}>New Sale</Button>
          </div>
        </div>

        <Card className="mx-auto max-w-sm print:border-0 print:shadow-none">
          <CardContent className="space-y-3 p-5 font-mono text-sm">
            <div className="text-center">
              <p className="text-base font-bold">StoreHub</p>
              <p className="text-xs text-muted-foreground">Retail Receipt</p>
            </div>
            <div className="border-t border-dashed border-border pt-2 text-xs">
              <p>Invoice: {postedSale.invoiceNumber}</p>
              <p>Date: {postedSale.saleDate}</p>
              <p>Customer: {postedSale.customer ? `${postedSale.customer.firstName} ${postedSale.customer.lastName || ''}` : 'Walk-in'}</p>
            </div>
            <div className="border-t border-dashed border-border pt-2">
              {postedSale.items.map((item) => (
                <div key={item.id} className="flex justify-between text-xs">
                  <span className="truncate pr-2">{item.product.name} x{item.quantity}</span>
                  <span>{money(item.subtotal)}</span>
                </div>
              ))}
            </div>
            <div className="space-y-1 border-t border-dashed border-border pt-2 text-xs">
              <div className="flex justify-between"><span>Taxable</span><span>{money(postedSale.taxableAmount)}</span></div>
              <div className="flex justify-between"><span>Tax</span><span>{money(postedSale.totalTax)}</span></div>
              <div className="flex justify-between text-sm font-bold"><span>TOTAL</span><span>{money(postedSale.totalAmount)}</span></div>
              <div className="flex justify-between"><span>Payment</span><span>{postedSale.paymentMode}</span></div>
            </div>
            <p className="border-t border-dashed border-border pt-2 text-center text-xs text-muted-foreground">
              Thank you for shopping with us!
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="POS / Counter Sale"
        description="Scan a barcode or search by name/SKU. Same accounting, inventory and GST engine as regular sales."
        actions={
          <Button variant="outline" onClick={() => navigate('/sales')} className="print:hidden">
            Exit POS
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card>
            <CardContent className="p-4">
              <div className="relative">
                <Scan className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  ref={searchRef}
                  autoFocus
                  placeholder="Scan barcode or search by name / SKU…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={handleSearchKeyDown}
                  onFocus={() => setShowResults(true)}
                  className="pl-9 pr-9 text-base"
                />
                {searching && <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />}
              </div>

              {showResults && results.length > 0 && (
                <div className="mt-2 max-h-64 overflow-y-auto rounded-md border border-border">
                  {results.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => addToCart(p)}
                      className="flex w-full items-center justify-between gap-3 border-b border-border px-3 py-2 text-left text-sm last:border-0 hover:bg-accent"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-medium">{p.name}</span>
                        <span className="block text-xs text-muted-foreground">
                          {p.sku} {p.barcode ? `· ${p.barcode}` : ''} · Stock: {p.stockQuantity}
                        </span>
                      </span>
                      <span className="shrink-0 font-medium">{money(p.sellingPrice)}</span>
                    </button>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead className="w-28">Qty</TableHead>
                    <TableHead className="w-28">Rate</TableHead>
                    <TableHead className="w-24">Disc.</TableHead>
                    <TableHead className="w-20">GST%</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lineTotals.map((l) => (
                    <TableRow key={l.productId}>
                      <TableCell className="max-w-[180px]">
                        <p className="truncate font-medium">{l.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{l.sku}</p>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1">
                          <Button type="button" variant="outline" size="icon" className="h-7 w-7 shrink-0"
                            onClick={() => updateLine(l.productId, { qty: Math.max(1, l.qty - 1) })}>
                            <Minus className="h-3 w-3" />
                          </Button>
                          <Input
                            type="number"
                            min={1}
                            className="h-7 w-12 px-1 text-center"
                            value={l.qty}
                            onChange={(e) => updateLine(l.productId, { qty: Math.max(1, Number(e.target.value) || 1) })}
                          />
                          <Button type="button" variant="outline" size="icon" className="h-7 w-7 shrink-0"
                            onClick={() => updateLine(l.productId, { qty: l.qty + 1 })}>
                            <Plus className="h-3 w-3" />
                          </Button>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} className="h-7 w-24" value={l.rate}
                          onChange={(e) => updateLine(l.productId, { rate: Number(e.target.value) || 0 })} />
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} className="h-7 w-20" value={l.discount}
                          onChange={(e) => updateLine(l.productId, { discount: Number(e.target.value) || 0 })} />
                      </TableCell>
                      <TableCell>
                        <Input type="number" min={0} className="h-7 w-16" value={l.gstPercent}
                          onChange={(e) => updateLine(l.productId, { gstPercent: Number(e.target.value) || 0 })} />
                      </TableCell>
                      <TableCell className="text-right font-medium">{money(l.total)}</TableCell>
                      <TableCell>
                        <Button type="button" variant="ghost" size="icon" className="h-7 w-7 text-destructive"
                          onClick={() => removeLine(l.productId)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {cart.length === 0 && (
                <div className="flex flex-col items-center gap-2 py-12 text-center text-sm text-muted-foreground">
                  <Scan className="h-8 w-8" />
                  <p>Cart is empty — scan or search to add items</p>
                </div>
              )}

              {cart.length > 0 && (
                <div className="flex justify-end border-t border-border p-3">
                  <Button type="button" variant="ghost" size="sm" onClick={clearCart} className="text-muted-foreground">
                    <X className="h-3.5 w-3.5" /> Clear Cart
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardContent className="space-y-3 p-4">
              <Label className="text-xs text-muted-foreground">Customer</Label>
              {customer ? (
                <div className="flex items-center justify-between rounded-md border border-border p-2">
                  <div>
                    <p className="text-sm font-medium">{customer.firstName} {customer.lastName || ''}</p>
                    <p className="text-xs text-muted-foreground">{customer.mobile}</p>
                  </div>
                  <Button type="button" variant="ghost" size="sm" onClick={() => setCustomer(null)}>Change</Button>
                </div>
              ) : (
                <div className="relative">
                  <Input
                    placeholder="Walk-in customer (search to select)"
                    value={customerQuery}
                    onChange={(e) => setCustomerQuery(e.target.value)}
                  />
                  {customerResults.length > 0 && (
                    <div className="absolute z-10 mt-1 w-full rounded-md border border-border bg-popover shadow-lg">
                      {customerResults.map((c) => (
                        <button
                          key={c.id}
                          type="button"
                          className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-accent"
                          onClick={() => {
                            setCustomer(c);
                            setCustomerQuery('');
                            setCustomerResults([]);
                          }}
                        >
                          <span>{c.firstName} {c.lastName || ''}</span>
                          <span className="text-xs text-muted-foreground">{c.mobile}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}

              <Label className="text-xs text-muted-foreground">Payment Mode</Label>
              <Select value={paymentMode} onValueChange={(v) => setPaymentMode(v as PaymentMode)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="CARD">Card</SelectItem>
                  <SelectItem value="UPI">UPI</SelectItem>
                  <SelectItem value="BANK">Bank Transfer</SelectItem>
                  <SelectItem value="OTHER">Other</SelectItem>
                </SelectContent>
              </Select>

              {isGst && (
                <>
                  <Label className="text-xs text-muted-foreground">Tax Mode</Label>
                  <Select value={taxMode} onValueChange={(v) => setTaxMode(v as 'INTRA_STATE' | 'INTER_STATE')}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INTRA_STATE">Intra-State (CGST+SGST)</SelectItem>
                      <SelectItem value="INTER_STATE">Inter-State (IGST)</SelectItem>
                    </SelectContent>
                  </Select>
                </>
              )}

              <Label className="text-xs text-muted-foreground">Amount Paid</Label>
              <Input type="number" min={0} value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)} />
              {Number(paidAmount) < grandTotal && (
                <p className="text-xs text-warning">
                  Partial/credit payment — remaining {money(grandTotal - (Number(paidAmount) || 0))} will be due
                  {!customer && ' (select a customer to allow this)'}.
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-1.5 p-4 text-sm">
              <div className="flex justify-between"><span className="text-muted-foreground">Subtotal</span><span>{money(subtotal)}</span></div>
              {totalDiscount > 0 && (
                <div className="flex justify-between"><span className="text-muted-foreground">Discount</span><span>-{money(totalDiscount)}</span></div>
              )}
              {isGst && taxMode === 'INTRA_STATE' && (
                <>
                  <div className="flex justify-between"><span className="text-muted-foreground">CGST</span><span>{money(cgst)}</span></div>
                  <div className="flex justify-between"><span className="text-muted-foreground">SGST</span><span>{money(sgst)}</span></div>
                </>
              )}
              {isGst && taxMode === 'INTER_STATE' && (
                <div className="flex justify-between"><span className="text-muted-foreground">IGST</span><span>{money(igst)}</span></div>
              )}
              <div className="flex justify-between border-t border-border pt-1.5 text-base font-bold">
                <span>Grand Total</span><span>{money(grandTotal)}</span>
              </div>
            </CardContent>
          </Card>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <Button
            className="w-full"
            size="lg"
            disabled={cart.length === 0 || postState === 'saving'}
            onClick={handlePost}
          >
            {postState === 'saving' ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" /> Saving…
              </>
            ) : (
              `Post Sale — ${money(grandTotal)}`
            )}
          </Button>
          {postState === 'failed' && (
            <p className="text-center text-xs text-muted-foreground">
              Not charged. Safe to retry — the same sale won't be created twice.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
