import { ReactNode, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { LucideIcon, MoreHorizontal, Plus, Search, ShieldCheck, Ban } from 'lucide-react';
import { parseApiError } from '../../utils/apiError';
import { BackButton } from '@/components/BackButton';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
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

export interface MasterRow {
  id: number;
  status: string;
}

export interface MasterColumn<T> {
  header: string;
  render: (item: T) => ReactNode;
  className?: string;
}

interface MasterListResponse<T> {
  content: T[];
  totalPages: number;
}

export interface MasterCrudPageProps<T extends MasterRow, TPayload> {
  icon: LucideIcon;
  title: string;
  description: string;
  itemLabel: string;
  searchPlaceholder: string;
  columns: MasterColumn<T>[];
  emptyValues: TPayload;
  toFormValues: (item: T) => TPayload;
  renderForm: (values: TPayload, setValues: (updater: (prev: TPayload) => TPayload) => void, fieldErrors: Record<string, string>) => ReactNode;
  renderView?: (item: T) => ReactNode;
  fetchList: (params: { search: string; status: string; page: number; size: number }) => Promise<{ data: MasterListResponse<T> }>;
  create: (payload: TPayload) => Promise<any>;
  update: (id: number, payload: TPayload) => Promise<any>;
  activate: (id: number) => Promise<any>;
  deactivate: (id: number) => Promise<any>;
  extraFilterSlot?: ReactNode;
  reloadToken?: unknown;
  backTo?: string;
  backLabel?: string;
}

const PAGE_SIZE = 10;
const ALL = '__all__';

export function MasterCrudPage<T extends MasterRow, TPayload>({
  icon: Icon,
  title,
  description,
  itemLabel,
  searchPlaceholder,
  columns,
  emptyValues,
  toFormValues,
  renderForm,
  renderView,
  fetchList,
  create,
  update,
  activate,
  deactivate,
  extraFilterSlot,
  reloadToken,
  backTo = '/masters',
  backLabel = 'Back to Masters',
}: MasterCrudPageProps<T, TPayload>) {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; item: T | null }>({
    show: false,
    mode: 'add',
    item: null,
  });
  const [values, setValues] = useState<TPayload>(emptyValues);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [viewItem, setViewItem] = useState<T | null>(null);
  const [discardConfirmOpen, setDiscardConfirmOpen] = useState(false);
  const initialValuesSnapshot = useRef<string | null>(null);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetchList({ search, status: statusFilter, page, size: PAGE_SIZE });
      setItems(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, `Failed to load ${itemLabel} list`).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter, reloadToken]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  const openAdd = () => {
    setValues(emptyValues);
    initialValuesSnapshot.current = JSON.stringify(emptyValues);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'add', item: null });
  };

  const openEdit = (item: T) => {
    const formValues = toFormValues(item);
    setValues(formValues);
    initialValuesSnapshot.current = JSON.stringify(formValues);
    setError('');
    setFieldErrors({});
    setFormModal({ show: true, mode: 'edit', item });
  };

  const closeForm = () => setFormModal((prev) => ({ ...prev, show: false }));

  const isFormDirty = () =>
    initialValuesSnapshot.current !== null && JSON.stringify(values) !== initialValuesSnapshot.current;

  const requestCloseForm = () => {
    if (isFormDirty()) {
      setDiscardConfirmOpen(true);
    } else {
      closeForm();
    }
  };

  const confirmDiscard = () => {
    setDiscardConfirmOpen(false);
    closeForm();
  };

  const cancelDiscard = () => setDiscardConfirmOpen(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      if (formModal.mode === 'add') {
        await create(values);
        toast.success(`${title} created successfully`);
      } else if (formModal.item) {
        await update(formModal.item.id, values);
        toast.success(`${title} updated successfully`);
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

  const toggleStatus = async (item: T) => {
    try {
      if (item.status === 'ACTIVE') {
        await deactivate(item.id);
        toast.success(`${title} deactivated successfully`);
      } else {
        await activate(item.id);
        toast.success(`${title} activated successfully`);
      }
      load();
    } catch (err) {
      toast.error(parseApiError(err, `Failed to update ${itemLabel} status`).message);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label={backLabel} onClick={() => navigate(backTo)} />

      <PageHeader
        title={title}
        description={description}
        actions={
          <Button onClick={openAdd}>
            <Plus className="h-4 w-4" />
            Add {title}
          </Button>
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={searchPlaceholder}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            {extraFilterSlot}
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
                {columns.map((c) => (
                  <TableHead key={c.header} className={c.className}>
                    {c.header}
                  </TableHead>
                ))}
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={columns.length + 2} />
            ) : (
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id}>
                    {columns.map((c) => (
                      <TableCell key={c.header} className={c.className}>
                        {c.render(item)}
                      </TableCell>
                    ))}
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
                          {renderView && <DropdownMenuItem onClick={() => setViewItem(item)}>View</DropdownMenuItem>}
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
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && items.length === 0 && (
            <EmptyState
              icon={Icon}
              title={`No ${itemLabel} found`}
              description="Try adjusting your search or filters, or add a new one."
            />
          )}

          {!loading && items.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={formModal.show} onOpenChange={(open) => !open && requestCloseForm()}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{formModal.mode === 'add' ? `Add ${title}` : `Edit ${title}`}</DialogTitle>
            <DialogDescription>
              {formModal.mode === 'add'
                ? `Create a new ${title.toLowerCase()}.`
                : `Update this ${title.toLowerCase()}'s details.`}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {renderForm(values, (updater) => setValues((prev) => updater(prev)), fieldErrors)}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={requestCloseForm} disabled={submitting}>
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
        description={`You have unsaved changes to this ${title.toLowerCase()}. Leaving now will discard them.`}
        onConfirm={confirmDiscard}
        onCancel={cancelDiscard}
      />

      {renderView && (
        <Dialog open={!!viewItem} onOpenChange={(open) => !open && setViewItem(null)}>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>{title} Details</DialogTitle>
            </DialogHeader>
            {viewItem && renderView(viewItem)}
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
