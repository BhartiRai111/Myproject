import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CreditCard, MoreHorizontal, Plus, Ban, ShieldCheck } from 'lucide-react';
import { paymentMethodApi } from '../../api/paymentMethodApi';
import { parseApiError } from '../../utils/apiError';
import { PaymentMethod, PaymentMethodPayload } from '../../types/paymentMethod';
import { PaymentMode } from '../../types/sale';
import { useAuth } from '@/context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Alert, AlertDescription } from '@/components/ui/alert';

const PAYMENT_MODES: PaymentMode[] = ['CASH', 'BANK', 'UPI', 'CARD', 'OTHER'];
const EMPTY: PaymentMethodPayload = { name: '', type: 'CASH', sortOrder: undefined };

export default function PaymentMethods() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [loading, setLoading] = useState(true);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; item: PaymentMethod | null }>({
    show: false,
    mode: 'add',
    item: null,
  });
  const [values, setValues] = useState<PaymentMethodPayload>(EMPTY);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await paymentMethodApi.listAll();
      setMethods(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load payment methods').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openAdd = () => {
    setValues(EMPTY);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'add', item: null });
  };

  const openEdit = (item: PaymentMethod) => {
    setValues({ name: item.name, type: item.type, sortOrder: item.sortOrder });
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'edit', item });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      if (formModal.mode === 'add') {
        await paymentMethodApi.create(values);
        toast.success('Payment method created');
      } else if (formModal.item) {
        await paymentMethodApi.update(formModal.item.id, values);
        toast.success('Payment method updated');
      }
      setFormModal((p) => ({ ...p, show: false }));
      load();
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save payment method');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActive = async (item: PaymentMethod) => {
    try {
      if (item.active) {
        await paymentMethodApi.deactivate(item.id);
        toast.success(`${item.name} deactivated`);
      } else {
        await paymentMethodApi.activate(item.id);
        toast.success(`${item.name} activated`);
      }
      load();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to update payment method status').message);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Payment Methods"
        description="Manage the payment methods available in POS, Sales, Purchases, Expenses, and Cash entries. Deactivate instead of deleting — a method used in historical transactions is never removed."
        actions={
          isAdmin ? (
            <Button onClick={openAdd}>
              <Plus className="h-4 w-4" /> New Payment Method
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
                <TableHead>Type</TableHead>
                <TableHead>Sort Order</TableHead>
                <TableHead>Status</TableHead>
                {isAdmin && <TableHead className="text-right">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i}>
                    <TableCell colSpan={isAdmin ? 5 : 4}>
                      <Skeleton className="h-6 w-full" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            ) : (
              <TableBody>
                {methods.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell className="font-medium">{m.name}</TableCell>
                    <TableCell className="text-muted-foreground">{m.type}</TableCell>
                    <TableCell>{m.sortOrder ?? '—'}</TableCell>
                    <TableCell>
                      <Badge variant={m.active ? 'success' : 'muted'}>{m.active ? 'ACTIVE' : 'INACTIVE'}</Badge>
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
                            <DropdownMenuItem onClick={() => openEdit(m)}>Edit</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => toggleActive(m)} variant={m.active ? 'destructive' : 'default'}>
                              {m.active ? (
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
                    )}
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && methods.length === 0 && (
            <EmptyState icon={CreditCard} title="No payment methods yet" description="Add the first payment method to get started." />
          )}
        </CardContent>
      </Card>

      <Dialog open={formModal.show} onOpenChange={(open) => !open && setFormModal((p) => ({ ...p, show: false }))}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{formModal.mode === 'add' ? 'New Payment Method' : 'Edit Payment Method'}</DialogTitle>
            <DialogDescription>
              {formModal.mode === 'add' ? 'Create a new payment method.' : "Update this payment method's details."}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="pmName">Name</Label>
              <Input
                id="pmName"
                required
                value={values.name}
                onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
                invalid={!!fieldErrors.name}
              />
              {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Type</Label>
              <Select value={values.type} onValueChange={(v) => setValues((p) => ({ ...p, type: v as PaymentMode }))}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_MODES.map((m) => (
                    <SelectItem key={m} value={m}>
                      {m}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pmSort">Sort Order (optional)</Label>
              <Input
                id="pmSort"
                type="number"
                value={values.sortOrder ?? ''}
                onChange={(e) => setValues((p) => ({ ...p, sortOrder: e.target.value ? Number(e.target.value) : undefined }))}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormModal((p) => ({ ...p, show: false }))} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" loading={submitting}>
                {formModal.mode === 'add' ? 'Create' : 'Save Changes'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
