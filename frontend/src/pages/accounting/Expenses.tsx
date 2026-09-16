import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Search, Plus, Receipt, BarChart3 } from 'lucide-react';
import { expenseApi } from '../../api/expenseApi';
import { expenseCategoryApi } from '../../api/mastersApi';
import { supplierApi } from '../../api/supplierApi';
import { parseApiError } from '../../utils/apiError';
import { Expense, ExpenseStatus } from '../../types/expense';
import { ExpenseCategory } from '../../types/masters';
import { Supplier } from '../../types/supplier';
import { PaymentMode } from '../../types/sale';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const PAGE_SIZE = 10;
const ALL = '__all__';
const PAYMENT_MODES: PaymentMode[] = ['CASH', 'BANK', 'UPI', 'CARD', 'OTHER'];
const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function statusVariant(status: ExpenseStatus) {
  if (status === 'POSTED') return 'success' as const;
  if (status === 'CANCELLED') return 'destructive' as const;
  return 'muted' as const;
}

export default function Expenses() {
  const navigate = useNavigate();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ExpenseStatus | ''>('');
  const [categoryId, setCategoryId] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [paymentMode, setPaymentMode] = useState<PaymentMode | ''>('');
  const [gstApplicable, setGstApplicable] = useState('');
  const [itcEligible, setItcEligible] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    Promise.all([expenseCategoryApi.listActive(), supplierApi.list({ size: 200 })]).then(([catRes, supRes]) => {
      setCategories(catRes.data);
      setSuppliers(supRes.data.content);
    });
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await expenseApi.list({
        search,
        status: status || undefined,
        categoryId: categoryId ? Number(categoryId) : undefined,
        supplierId: supplierId ? Number(supplierId) : undefined,
        paymentMode: paymentMode || undefined,
        gstApplicable: gstApplicable ? gstApplicable === 'true' : undefined,
        itcEligible: itcEligible ? itcEligible === 'true' : undefined,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        page,
        size: PAGE_SIZE,
      });
      setExpenses(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load expenses').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status, categoryId, supplierId, paymentMode, gstApplicable, itcEligible, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Expenses"
        description="Operational business expenses — rent, electricity, transport, and more. Posts through the same accounting engine as every other voucher."
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => navigate('/accounting/reports/expense-analysis')}>
              <BarChart3 className="h-4 w-4" /> Expense Analysis
            </Button>
            <Button onClick={() => navigate('/accounting/expenses/new')}>
              <Plus className="h-4 w-4" /> New Expense
            </Button>
          </div>
        }
      />

      <Card>
        <CardContent className="space-y-3 p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by expense number, category, or vendor"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Button type="submit" variant="secondary" className="sm:w-auto">
              Search
            </Button>
          </form>

          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-8">
            <Select value={status || ALL} onValueChange={(v) => { setPage(0); setStatus(v === ALL ? '' : (v as ExpenseStatus)); }}>
              <SelectTrigger><SelectValue placeholder="Status" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Statuses</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="POSTED">Posted</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
              </SelectContent>
            </Select>
            <Select value={categoryId || ALL} onValueChange={(v) => { setPage(0); setCategoryId(v === ALL ? '' : v); }}>
              <SelectTrigger><SelectValue placeholder="Category" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Categories</SelectItem>
                {categories.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={supplierId || ALL} onValueChange={(v) => { setPage(0); setSupplierId(v === ALL ? '' : v); }}>
              <SelectTrigger><SelectValue placeholder="Party" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Parties</SelectItem>
                {suppliers.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={paymentMode || ALL} onValueChange={(v) => { setPage(0); setPaymentMode(v === ALL ? '' : (v as PaymentMode)); }}>
              <SelectTrigger><SelectValue placeholder="Payment Mode" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Payment Modes</SelectItem>
                {PAYMENT_MODES.map((m) => <SelectItem key={m} value={m}>{m}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={gstApplicable || ALL} onValueChange={(v) => { setPage(0); setGstApplicable(v === ALL ? '' : v); }}>
              <SelectTrigger><SelectValue placeholder="GST" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>GST: Any</SelectItem>
                <SelectItem value="true">GST Applicable</SelectItem>
                <SelectItem value="false">Non-GST</SelectItem>
              </SelectContent>
            </Select>
            <Select value={itcEligible || ALL} onValueChange={(v) => { setPage(0); setItcEligible(v === ALL ? '' : v); }}>
              <SelectTrigger><SelectValue placeholder="ITC" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>ITC: Any</SelectItem>
                <SelectItem value="true">ITC Eligible</SelectItem>
                <SelectItem value="false">ITC Ineligible</SelectItem>
              </SelectContent>
            </Select>
            <div>
              <Label className="text-xs text-muted-foreground">From</Label>
              <Input type="date" value={fromDate} onChange={(e) => { setPage(0); setFromDate(e.target.value); }} />
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">To</Label>
              <Input type="date" value={toDate} onChange={(e) => { setPage(0); setToDate(e.target.value); }} />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Expense Number</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Vendor</TableHead>
                <TableHead>Payment Mode</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {expenses.map((e) => (
                  <TableRow key={e.id} className="cursor-pointer" onClick={() => navigate(`/accounting/expenses/${e.id}`)}>
                    <TableCell className="font-mono text-sm font-medium">{e.expenseNumber}</TableCell>
                    <TableCell>{e.expenseDate}</TableCell>
                    <TableCell>{e.category}</TableCell>
                    <TableCell className="text-muted-foreground">{e.vendorName || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{e.supplierId ? 'Credit' : e.paymentMode}</TableCell>
                    <TableCell>{money(e.totalAmount)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(e.status)}>{e.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && expenses.length === 0 && (
            <EmptyState icon={Receipt} title="No expenses found" description="Record an operational expense to get started." />
          )}

          {!loading && expenses.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
