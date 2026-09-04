import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Pencil, Plus, X } from 'lucide-react';
import { categoryApi } from '../api/categoryApi';
import { parseApiError } from '../utils/apiError';
import { Category } from '../types/product';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  show: boolean;
  onClose: () => void;
  onChanged: () => void;
}

export default function CategoryManagerDialog({ show, onClose, onChanged }: Props) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [itemType, setItemType] = useState('');
  const [applicableProperty, setApplicableProperty] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editItemType, setEditItemType] = useState('');
  const [editApplicableProperty, setEditApplicableProperty] = useState('');
  const [editError, setEditError] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  const loadCategories = async () => {
    setLoading(true);
    try {
      const res = await categoryApi.list();
      setCategories(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load categories').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (show) {
      setName('');
      setDescription('');
      setItemType('');
      setApplicableProperty('');
      setError('');
      setFieldErrors({});
      setEditingId(null);
      loadCategories();
    }
  }, [show]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    try {
      await categoryApi.create({ name, description, itemType, applicableProperty });
      setName('');
      setDescription('');
      setItemType('');
      setApplicableProperty('');
      toast.success('Category created successfully');
      await loadCategories();
      onChanged();
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to add category');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = (category: Category) => {
    setEditingId(category.id);
    setEditName(category.name);
    setEditDescription(category.description || '');
    setEditItemType(category.itemType || '');
    setEditApplicableProperty(category.applicableProperty || '');
    setEditError('');
  };

  const handleSaveEdit = async (id: number) => {
    setSavingEdit(true);
    setEditError('');
    try {
      await categoryApi.update(id, {
        name: editName,
        description: editDescription,
        itemType: editItemType,
        applicableProperty: editApplicableProperty,
      });
      toast.success('Category updated successfully');
      setEditingId(null);
      await loadCategories();
      onChanged();
    } catch (err) {
      setEditError(parseApiError(err, 'Failed to update category').message);
    } finally {
      setSavingEdit(false);
    }
  };

  const handleToggleStatus = async (category: Category) => {
    setTogglingId(category.id);
    try {
      if (category.status === 'ACTIVE') {
        await categoryApi.deactivate(category.id);
        toast.success('Category deactivated');
      } else {
        await categoryApi.activate(category.id);
        toast.success('Category activated');
      }
      await loadCategories();
      onChanged();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to update category status').message);
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Manage Categories</DialogTitle>
          <DialogDescription>Add, edit, or deactivate product categories.</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleAdd} className="space-y-3" noValidate>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="flex gap-2">
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="catName">Name</Label>
              <Input
                id="catName"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                invalid={!!fieldErrors.name}
              />
            </div>
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="catDescription">Description</Label>
              <Input id="catDescription" value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div className="flex items-end">
              <Button type="submit" loading={submitting}>
                <Plus className="h-4 w-4" /> Add
              </Button>
            </div>
          </div>
          <div className="flex gap-2">
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="catItemType">Item Type</Label>
              <Input
                id="catItemType"
                placeholder="e.g. Goods, Service"
                value={itemType}
                onChange={(e) => setItemType(e.target.value)}
              />
            </div>
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="catApplicableProperty">Applicable Property</Label>
              <Input
                id="catApplicableProperty"
                value={applicableProperty}
                onChange={(e) => setApplicableProperty(e.target.value)}
              />
            </div>
          </div>
        </form>

        <Separator />

        <div className="max-h-80 overflow-y-auto rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {!loading &&
                categories.map((c) =>
                  editingId === c.id ? (
                    <TableRow key={c.id}>
                      <TableCell colSpan={3}>
                        <div className="space-y-2 py-1">
                          {editError && <p className="text-xs text-destructive">{editError}</p>}
                          <Input value={editName} onChange={(e) => setEditName(e.target.value)} placeholder="Name" />
                          <Input
                            value={editDescription}
                            onChange={(e) => setEditDescription(e.target.value)}
                            placeholder="Description"
                          />
                          <Input
                            value={editItemType}
                            onChange={(e) => setEditItemType(e.target.value)}
                            placeholder="Item Type"
                          />
                          <Input
                            value={editApplicableProperty}
                            onChange={(e) => setEditApplicableProperty(e.target.value)}
                            placeholder="Applicable Property"
                          />
                          <div className="flex gap-2">
                            <Button type="button" size="sm" loading={savingEdit} onClick={() => handleSaveEdit(c.id)}>
                              Save
                            </Button>
                            <Button type="button" size="sm" variant="outline" onClick={() => setEditingId(null)}>
                              <X className="h-4 w-4" /> Cancel
                            </Button>
                          </div>
                        </div>
                      </TableCell>
                    </TableRow>
                  ) : (
                    <TableRow key={c.id}>
                      <TableCell>
                        <div className="font-medium">{c.name}</div>
                        {c.description && <div className="text-xs text-muted-foreground">{c.description}</div>}
                      </TableCell>
                      <TableCell>
                        <Badge variant={c.status === 'ACTIVE' ? 'success' : 'muted'}>{c.status}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button type="button" variant="ghost" size="icon" className="h-8 w-8" onClick={() => startEdit(c)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            loading={togglingId === c.id}
                            onClick={() => handleToggleStatus(c)}
                          >
                            {c.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                )}
            </TableBody>
          </Table>
          {!loading && categories.length === 0 && (
            <p className="p-6 text-center text-sm text-muted-foreground">No categories yet. Add one above.</p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
