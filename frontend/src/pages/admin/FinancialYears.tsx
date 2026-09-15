import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CalendarRange, MoreHorizontal, Plus, Star, Lock, Unlock } from 'lucide-react';
import { financialYearApi } from '../../api/financialYearApi';
import { parseApiError } from '../../utils/apiError';
import { FinancialYear, FinancialYearCreatePayload, FinancialYearSummary } from '../../types/financialYear';
import { useAuth } from '@/context/AuthContext';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Alert, AlertDescription } from '@/components/ui/alert';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const EMPTY: FinancialYearCreatePayload = { startDate: '', endDate: '', name: '', code: '' };

export default function FinancialYears() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [years, setYears] = useState<FinancialYear[]>([]);
  const [loading, setLoading] = useState(true);

  const [formOpen, setFormOpen] = useState(false);
  const [values, setValues] = useState<FinancialYearCreatePayload>(EMPTY);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const [closeConfirm, setCloseConfirm] = useState<FinancialYear | null>(null);
  const [markCurrentConfirm, setMarkCurrentConfirm] = useState<FinancialYear | null>(null);

  const [summaryFor, setSummaryFor] = useState<FinancialYear | null>(null);
  const [summary, setSummary] = useState<FinancialYearSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await financialYearApi.list();
      setYears(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load financial years').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setValues(EMPTY);
    setError('');
    setFieldErrors({});
    setFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await financialYearApi.create(values);
      toast.success('Financial year created');
      setFormOpen(false);
      load();
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to create financial year');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpen = async (fy: FinancialYear) => {
    try {
      await financialYearApi.open(fy.id);
      toast.success(`${fy.name} re-opened`);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to open financial year').message);
    }
  };

  const handleClose = async () => {
    if (!closeConfirm) return;
    try {
      await financialYearApi.close(closeConfirm.id);
      toast.success(`${closeConfirm.name} closed`);
      setCloseConfirm(null);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to close financial year').message);
      setCloseConfirm(null);
    }
  };

  const handleMarkCurrent = async () => {
    if (!markCurrentConfirm) return;
    try {
      await financialYearApi.markCurrent(markCurrentConfirm.id);
      toast.success(`${markCurrentConfirm.name} marked as current`);
      setMarkCurrentConfirm(null);
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to mark current').message);
      setMarkCurrentConfirm(null);
    }
  };

  const openSummary = async (fy: FinancialYear) => {
    setSummaryFor(fy);
    setSummary(null);
    setSummaryLoading(true);
    try {
      const res = await financialYearApi.getSummary(fy.id);
      setSummary(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load summary').message);
    } finally {
      setSummaryLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Financial Years"
        description="Manage financial years, open/close periods, and set the current year. Voucher numbering and posting are both FY-aware."
        actions={
          isAdmin ? (
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" /> New Financial Year
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Code</TableHead>
                <TableHead>Start Date</TableHead>
                <TableHead>End Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Current</TableHead>
                {isAdmin && <TableHead className="text-right">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i}>
                    <TableCell colSpan={isAdmin ? 7 : 6}>
                      <Skeleton className="h-6 w-full" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            ) : (
              <TableBody>
                {years.map((fy) => (
                  <TableRow key={fy.id}>
                    <TableCell className="font-medium">{fy.name}</TableCell>
                    <TableCell className="font-mono text-sm">{fy.code}</TableCell>
                    <TableCell>{fy.startDate}</TableCell>
                    <TableCell>{fy.endDate}</TableCell>
                    <TableCell>
                      <Badge variant={fy.status === 'OPEN' ? 'success' : 'muted'}>{fy.status}</Badge>
                    </TableCell>
                    <TableCell>
                      {fy.current && (
                        <Badge variant="default">
                          <Star className="h-3 w-3" /> Current
                        </Badge>
                      )}
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => openSummary(fy)}>View Summary</DropdownMenuItem>
                            {!fy.current && fy.status === 'OPEN' && (
                              <DropdownMenuItem onClick={() => setMarkCurrentConfirm(fy)}>
                                <Star className="h-4 w-4" /> Mark as Current
                              </DropdownMenuItem>
                            )}
                            {fy.status === 'OPEN' ? (
                              <DropdownMenuItem onClick={() => setCloseConfirm(fy)}>
                                <Lock className="h-4 w-4" /> Close Year
                              </DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem onClick={() => handleOpen(fy)}>
                                <Unlock className="h-4 w-4" /> Re-open Year
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && years.length === 0 && (
            <EmptyState icon={CalendarRange} title="No financial years yet" description="Create the first financial year to get started." />
          )}
        </CardContent>
      </Card>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>New Financial Year</DialogTitle>
            <DialogDescription>Indian convention is 1 April to 31 March. Name/code are derived automatically if left blank.</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="fyStart">Start Date</Label>
                <Input
                  id="fyStart"
                  type="date"
                  required
                  value={values.startDate}
                  onChange={(e) => setValues((p) => ({ ...p, startDate: e.target.value }))}
                  invalid={!!fieldErrors.startDate}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="fyEnd">End Date</Label>
                <Input
                  id="fyEnd"
                  type="date"
                  required
                  value={values.endDate}
                  onChange={(e) => setValues((p) => ({ ...p, endDate: e.target.value }))}
                  invalid={!!fieldErrors.endDate}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fyName">Name (optional)</Label>
              <Input
                id="fyName"
                placeholder="FY 2026-27"
                value={values.name}
                onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fyCode">Code (optional)</Label>
              <Input
                id="fyCode"
                placeholder="26-27"
                value={values.code}
                onChange={(e) => setValues((p) => ({ ...p, code: e.target.value }))}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" loading={submitting}>
                Create
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!summaryFor} onOpenChange={(open) => !open && setSummaryFor(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{summaryFor?.name} Summary</DialogTitle>
          </DialogHeader>
          {summaryLoading || !summary ? (
            <Skeleton className="h-32 w-full" />
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between rounded-md border border-border p-3">
                <span className="text-sm text-muted-foreground">Total Sales ({summary.saleCount})</span>
                <span className="font-semibold">{money(summary.totalSales)}</span>
              </div>
              <div className="flex items-center justify-between rounded-md border border-border p-3">
                <span className="text-sm text-muted-foreground">Total Purchases ({summary.purchaseCount})</span>
                <span className="font-semibold">{money(summary.totalPurchases)}</span>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!closeConfirm}
        title={`Close ${closeConfirm?.name}?`}
        description="Once closed, no new transactions can be posted with a date in this financial year. You can re-open it later if needed."
        confirmLabel="Close Year"
        cancelLabel="Keep Open"
        onConfirm={handleClose}
        onCancel={() => setCloseConfirm(null)}
      />

      <ConfirmDialog
        open={!!markCurrentConfirm}
        title={`Mark ${markCurrentConfirm?.name} as current?`}
        description="This will unmark any other financial year as current. It does not affect historical transactions."
        confirmLabel="Mark as Current"
        cancelLabel="Cancel"
        destructive={false}
        onConfirm={handleMarkCurrent}
        onCancel={() => setMarkCurrentConfirm(null)}
      />
    </div>
  );
}
