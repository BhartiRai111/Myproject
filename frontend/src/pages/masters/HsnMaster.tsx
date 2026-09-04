import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { FileText, MoreHorizontal, Plus, Search, ShieldCheck, Ban, Trash2 } from 'lucide-react';
import { hsnApi } from '../../api/mastersApi';
import { Hsn, HsnPayload, HsnTaxRate } from '../../types/masters';
import { parseApiError } from '../../utils/apiError';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Alert, AlertDescription } from '@/components/ui/alert';

const PAGE_SIZE = 10;
const ALL = '__all__';

const emptyTaxRate = (): HsnTaxRate => ({
  taxPercent: 0,
  cgstPercent: undefined,
  sgstPercent: undefined,
  igstPercent: undefined,
  cessPercent: undefined,
  effectiveFrom: new Date().toISOString().slice(0, 10),
});

const EMPTY: HsnPayload = { hsnCode: '', description: '', taxRates: [emptyTaxRate()] };

export default function HsnMaster() {
  const [items, setItems] = useState<Hsn[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; item: Hsn | null }>({
    show: false,
    mode: 'add',
    item: null,
  });
  const [values, setValues] = useState<HsnPayload>(EMPTY);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [viewItem, setViewItem] = useState<Hsn | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await hsnApi.list({ search, status: statusFilter as any, page, size: PAGE_SIZE });
      setItems(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load HSN list').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const openAdd = () => {
    setValues(EMPTY);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'add', item: null });
  };

  const openEdit = (item: Hsn) => {
    setValues({
      hsnCode: item.hsnCode,
      description: item.description || '',
      taxRates: item.taxRates.length > 0 ? item.taxRates.map((r) => ({ ...r })) : [emptyTaxRate()],
    });
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'edit', item });
  };

  const closeForm = () => setFormModal((prev) => ({ ...prev, show: false }));

  const addTaxRateRow = () => setValues((p) => ({ ...p, taxRates: [...p.taxRates, emptyTaxRate()] }));
  const removeTaxRateRow = (idx: number) =>
    setValues((p) => ({ ...p, taxRates: p.taxRates.filter((_, i) => i !== idx) }));
  const updateTaxRateRow = (idx: number, field: keyof HsnTaxRate, value: string) =>
    setValues((p) => ({
      ...p,
      taxRates: p.taxRates.map((r, i) =>
        i === idx
          ? { ...r, [field]: field === 'effectiveFrom' ? value : value === '' ? undefined : Number(value) }
          : r
      ),
    }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      if (formModal.mode === 'add') {
        await hsnApi.create(values);
        toast.success('HSN code created successfully');
      } else if (formModal.item) {
        await hsnApi.update(formModal.item.id, values);
        toast.success('HSN code updated successfully');
      }
      closeForm();
      load();
    } catch (err) {
      const parsed = parseApiError(err, 'Something went wrong. Please try again.');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleStatus = async (item: Hsn) => {
    try {
      if (item.status === 'ACTIVE') {
        await hsnApi.deactivate(item.id);
        toast.success('HSN code deactivated successfully');
      } else {
        await hsnApi.activate(item.id);
        toast.success('HSN code activated successfully');
      }
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to update HSN status').message);
    }
  };

  const latestRate = (item: Hsn) =>
    item.taxRates.length > 0
      ? [...item.taxRates].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0]
      : null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="HSN Master"
        description="Manage HSN codes and their applicable tax rates."
        actions={
          <Button onClick={openAdd}>
            <Plus className="h-4 w-4" />
            Add HSN Code
          </Button>
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by HSN code or description"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <select
              value={statusFilter || ALL}
              onChange={(e) => {
                setPage(0);
                setStatusFilter(e.target.value === ALL ? '' : e.target.value);
              }}
              className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm sm:w-36"
            >
              <option value={ALL}>All Status</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
            <Button type="submit" variant="secondary" className="sm:w-auto">
              Search
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>HSN Code</TableHead>
                <TableHead>Description</TableHead>
                <TableHead>Current Tax %</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={5} />
            ) : (
              <TableBody>
                {items.map((item) => {
                  const rate = latestRate(item);
                  return (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.hsnCode}</TableCell>
                      <TableCell className="text-muted-foreground">{item.description || '-'}</TableCell>
                      <TableCell>{rate ? `${rate.taxPercent}%` : '-'}</TableCell>
                      <TableCell>
                        <Badge variant={item.status === 'ACTIVE' ? 'success' : 'muted'}>{item.status}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => setViewItem(item)}>View</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => openEdit(item)}>Edit</DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => toggleStatus(item)}
                              variant={item.status === 'ACTIVE' ? 'destructive' : 'default'}
                            >
                              {item.status === 'ACTIVE' ? (
                                <>
                                  <Ban className="h-4 w-4" /> Deactivate
                                </>
                              ) : (
                                <>
                                  <ShieldCheck className="h-4 w-4" /> Activate
                                </>
                              )}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            )}
          </Table>

          {!loading && items.length === 0 && (
            <EmptyState icon={FileText} title="No HSN codes found" description="Try adjusting your search or filters, or add a new one." />
          )}

          {!loading && items.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={formModal.show} onOpenChange={(open) => !open && closeForm()}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{formModal.mode === 'add' ? 'Add HSN Code' : 'Edit HSN Code'}</DialogTitle>
            <DialogDescription>
              {formModal.mode === 'add' ? 'Create a new HSN code with its applicable tax rates.' : "Update this HSN code's details."}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="hsnCode">HSN Code</Label>
                <Input
                  id="hsnCode"
                  required
                  value={values.hsnCode}
                  onChange={(e) => setValues((p) => ({ ...p, hsnCode: e.target.value }))}
                  invalid={!!fieldErrors.hsnCode}
                />
                {fieldErrors.hsnCode && <p className="text-xs text-destructive">{fieldErrors.hsnCode}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="hsnDescription">Description</Label>
                <Input
                  id="hsnDescription"
                  value={values.description}
                  onChange={(e) => setValues((p) => ({ ...p, description: e.target.value }))}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Tax Rates</Label>
                <Button type="button" variant="outline" size="sm" onClick={addTaxRateRow}>
                  <Plus className="h-3.5 w-3.5" /> Add Rate
                </Button>
              </div>
              <div className="space-y-2 rounded-md border border-border p-3">
                {values.taxRates.map((rate, idx) => (
                  <div key={idx} className="grid grid-cols-6 items-end gap-2 border-b border-border pb-2 last:border-0 last:pb-0">
                    <div className="space-y-1">
                      <Label className="text-xs">Tax %</Label>
                      <Input
                        type="number"
                        step="0.01"
                        required
                        value={rate.taxPercent}
                        onChange={(e) => updateTaxRateRow(idx, 'taxPercent', e.target.value)}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">CGST %</Label>
                      <Input
                        type="number"
                        step="0.01"
                        value={rate.cgstPercent ?? ''}
                        onChange={(e) => updateTaxRateRow(idx, 'cgstPercent', e.target.value)}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">SGST %</Label>
                      <Input
                        type="number"
                        step="0.01"
                        value={rate.sgstPercent ?? ''}
                        onChange={(e) => updateTaxRateRow(idx, 'sgstPercent', e.target.value)}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">IGST %</Label>
                      <Input
                        type="number"
                        step="0.01"
                        value={rate.igstPercent ?? ''}
                        onChange={(e) => updateTaxRateRow(idx, 'igstPercent', e.target.value)}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">Effective From</Label>
                      <Input
                        type="date"
                        required
                        value={rate.effectiveFrom}
                        onChange={(e) => updateTaxRateRow(idx, 'effectiveFrom', e.target.value)}
                      />
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="h-9 w-9 text-destructive"
                      disabled={values.taxRates.length <= 1}
                      onClick={() => removeTaxRateRow(idx)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeForm} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" loading={submitting}>
                {formModal.mode === 'add' ? 'Create' : 'Save Changes'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!viewItem} onOpenChange={(open) => !open && setViewItem(null)}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>HSN {viewItem?.hsnCode}</DialogTitle>
            <DialogDescription>{viewItem?.description || 'No description'}</DialogDescription>
          </DialogHeader>
          {viewItem && (
            <div className="space-y-2">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tax %</TableHead>
                    <TableHead>CGST</TableHead>
                    <TableHead>SGST</TableHead>
                    <TableHead>IGST</TableHead>
                    <TableHead>Cess</TableHead>
                    <TableHead>Effective From</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {viewItem.taxRates.map((r) => (
                    <TableRow key={r.id ?? `${r.taxPercent}-${r.effectiveFrom}`}>
                      <TableCell>{r.taxPercent}%</TableCell>
                      <TableCell>{r.cgstPercent ?? '-'}</TableCell>
                      <TableCell>{r.sgstPercent ?? '-'}</TableCell>
                      <TableCell>{r.igstPercent ?? '-'}</TableCell>
                      <TableCell>{r.cessPercent ?? '-'}</TableCell>
                      <TableCell>{r.effectiveFrom}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
