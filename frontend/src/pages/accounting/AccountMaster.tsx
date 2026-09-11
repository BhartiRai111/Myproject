import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Landmark, MoreHorizontal, Pencil, Plus, Search } from 'lucide-react';
import { accountApi } from '../../api/accountingApi';
import { parseApiError } from '../../utils/apiError';
import { Account, AccountGroup, AccountPayload, AccountType, LedgerEntryType } from '../../types/accounting';
import { useAuth } from '@/context/AuthContext';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Alert, AlertDescription } from '@/components/ui/alert';

const PAGE_SIZE = 20;
const ALL = '__all__';

const ACCOUNT_TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'INCOME', 'EXPENSE'];

const EMPTY: AccountPayload = {
  accountCode: '',
  accountName: '',
  accountType: 'ASSET',
  accountGroupId: null,
  openingBalance: 0,
  openingBalanceType: 'DEBIT',
  active: true,
};

export default function AccountMaster() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [groups, setGroups] = useState<AccountGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<AccountType | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; item: Account | null }>({
    show: false,
    mode: 'add',
    item: null,
  });
  const [values, setValues] = useState<AccountPayload>(EMPTY);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [discardConfirmOpen, setDiscardConfirmOpen] = useState(false);
  const initialSnapshot = useRef<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountApi.list({ search, accountType: typeFilter || undefined, page, size: PAGE_SIZE });
      setAccounts(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load accounts').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    accountApi.groups().then((res) => setGroups(res.data));
  }, []);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, typeFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const openAdd = () => {
    setValues(EMPTY);
    initialSnapshot.current = JSON.stringify(EMPTY);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'add', item: null });
  };

  const openEdit = (account: Account) => {
    const formValues: AccountPayload = {
      accountCode: account.accountCode,
      accountName: account.accountName,
      accountType: account.accountType,
      accountGroupId: account.accountGroupId,
      openingBalance: account.openingBalance,
      openingBalanceType: account.openingBalanceType,
      active: account.active,
    };
    setValues(formValues);
    initialSnapshot.current = JSON.stringify(formValues);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'edit', item: account });
  };

  const closeForm = () => setFormModal((prev) => ({ ...prev, show: false }));
  const isDirty = () => initialSnapshot.current !== null && JSON.stringify(values) !== initialSnapshot.current;
  const requestClose = () => (isDirty() ? setDiscardConfirmOpen(true) : closeForm());

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      if (formModal.mode === 'add') {
        await accountApi.create(values);
        toast.success('Account created successfully');
      } else if (formModal.item) {
        await accountApi.update(formModal.item.id, values);
        toast.success('Account updated successfully');
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

  const isSystemAccount = formModal.item?.systemAccount ?? false;

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Chart of Accounts"
        description="The full set of ledger accounts financial transactions post to."
        actions={
          isAdmin ? (
            <Button onClick={openAdd}>
              <Plus className="h-4 w-4" /> Add Account
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by account code or name"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={typeFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setTypeFilter(v === ALL ? '' : (v as AccountType));
              }}
            >
              <SelectTrigger className="w-full sm:w-40">
                <SelectValue placeholder="Account Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Types</SelectItem>
                {ACCOUNT_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
                <TableHead>Code</TableHead>
                <TableHead>Account Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Group</TableHead>
                <TableHead>Opening Balance</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {accounts.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="font-mono text-sm">{a.accountCode}</TableCell>
                    <TableCell className="font-medium">
                      {a.accountName}
                      {a.systemAccount && (
                        <Badge variant="outline" className="ml-2">
                          System
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>{a.accountType}</TableCell>
                    <TableCell className="text-muted-foreground">{a.accountGroupName || '—'}</TableCell>
                    <TableCell>
                      {a.openingBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })} {a.openingBalanceType}
                    </TableCell>
                    <TableCell>
                      <Badge variant={a.active ? 'success' : 'muted'}>{a.active ? 'ACTIVE' : 'INACTIVE'}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      {isAdmin ? (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => openEdit(a)}>
                              <Pencil className="h-4 w-4" /> Edit
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && accounts.length === 0 && (
            <EmptyState icon={Landmark} title="No accounts found" description="Try adjusting your search or filters." />
          )}

          {!loading && accounts.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={formModal.show} onOpenChange={(open) => !open && requestClose()}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{formModal.mode === 'add' ? 'Add Account' : 'Edit Account'}</DialogTitle>
            <DialogDescription>
              {formModal.mode === 'add' ? 'Create a new ledger account.' : "Update this account's details."}
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
                <Label htmlFor="accCode">Account Code</Label>
                <Input
                  id="accCode"
                  required
                  disabled={isSystemAccount}
                  value={values.accountCode}
                  onChange={(e) => setValues((p) => ({ ...p, accountCode: e.target.value }))}
                  invalid={!!fieldErrors.accountCode}
                />
                {fieldErrors.accountCode && <p className="text-xs text-destructive">{fieldErrors.accountCode}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="accType">Account Type</Label>
                <Select
                  value={values.accountType}
                  onValueChange={(v) => setValues((p) => ({ ...p, accountType: v as AccountType }))}
                  disabled={isSystemAccount}
                >
                  <SelectTrigger id="accType">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ACCOUNT_TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="accName">Account Name</Label>
              <Input
                id="accName"
                required
                value={values.accountName}
                onChange={(e) => setValues((p) => ({ ...p, accountName: e.target.value }))}
                invalid={!!fieldErrors.accountName}
              />
              {fieldErrors.accountName && <p className="text-xs text-destructive">{fieldErrors.accountName}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="accGroup">Account Group</Label>
              <Select
                value={values.accountGroupId ? String(values.accountGroupId) : 'none'}
                onValueChange={(v) => setValues((p) => ({ ...p, accountGroupId: v === 'none' ? null : Number(v) }))}
              >
                <SelectTrigger id="accGroup">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None</SelectItem>
                  {groups.map((g) => (
                    <SelectItem key={g.id} value={String(g.id)}>
                      {g.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="accOpening">Opening Balance</Label>
                <Input
                  id="accOpening"
                  type="number"
                  step="0.01"
                  min={0}
                  value={values.openingBalance}
                  onChange={(e) => setValues((p) => ({ ...p, openingBalance: Number(e.target.value) }))}
                  invalid={!!fieldErrors.openingBalance}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="accOpeningType">Opening Balance Type</Label>
                <Select
                  value={values.openingBalanceType}
                  onValueChange={(v) => setValues((p) => ({ ...p, openingBalanceType: v as LedgerEntryType }))}
                >
                  <SelectTrigger id="accOpeningType">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="DEBIT">Debit</SelectItem>
                    <SelectItem value="CREDIT">Credit</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-input accent-primary"
                checked={values.active}
                onChange={(e) => setValues((p) => ({ ...p, active: e.target.checked }))}
              />
              Active
            </label>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={requestClose} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" loading={submitting}>
                {formModal.mode === 'add' ? 'Create' : 'Save Changes'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={discardConfirmOpen}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this account. Leaving now will discard them."
        onConfirm={() => {
          setDiscardConfirmOpen(false);
          closeForm();
        }}
        onCancel={() => setDiscardConfirmOpen(false)}
      />
    </div>
  );
}
