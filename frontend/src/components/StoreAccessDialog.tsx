import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { userApi } from '../api/userApi';
import { storeApi } from '../api/storeApi';
import { parseApiError } from '../utils/apiError';
import { User } from '../types/user';
import { Store } from '../types/store';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

interface Props {
  show: boolean;
  user: User | null;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * ADMIN assigns which stores a user may act on (Multi-Store spec sections 11, 70) — the
 * ASSIGNED_STORES side of store access. A user holding STORE_ACCESS_ALL (e.g. ADMIN) already
 * sees every store regardless of this list, so this dialog is mainly for STORE_MANAGER/
 * STAFF/ACCOUNTANT-style roles scoped to specific branches.
 */
export default function StoreAccessDialog({ show, user, onClose, onSaved }: Props) {
  const [stores, setStores] = useState<Store[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!show || !user) return;
    setError('');
    setLoading(true);
    Promise.all([storeApi.list({ status: 'ACTIVE', size: 1000 }), userApi.getAssignedStores(user.id)])
      .then(([storesRes, assignedRes]) => {
        setStores(storesRes.data.content);
        setSelected(new Set(assignedRes.data));
      })
      .catch((err) => setError(parseApiError(err, 'Failed to load store access').message))
      .finally(() => setLoading(false));
  }, [show, user]);

  const toggle = (storeId: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(storeId)) {
        next.delete(storeId);
      } else {
        next.add(storeId);
      }
      return next;
    });
  };

  const handleSave = async () => {
    if (!user) return;
    setSubmitting(true);
    setError('');
    try {
      await userApi.assignStores(user.id, Array.from(selected));
      toast.success(`Store access updated for ${user.email}`);
      onSaved();
      onClose();
    } catch (err) {
      setError(parseApiError(err, 'Failed to update store access').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Store Access</DialogTitle>
          <DialogDescription>
            Choose which stores {user?.email} may act on. This has no effect on a user whose role already grants
            access to every store.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {loading ? (
          <p className="py-6 text-center text-sm text-muted-foreground">Loading stores…</p>
        ) : stores.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">No active stores found.</p>
        ) : (
          <div className="max-h-72 space-y-1 overflow-y-auto rounded-md border border-border p-2">
            {stores.map((store) => (
              <label
                key={store.id}
                className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent"
              >
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-input accent-primary"
                  checked={selected.has(store.id)}
                  onChange={() => toggle(store.id)}
                />
                <span className="flex-1">
                  {store.storeName} <span className="text-muted-foreground">({store.storeCode})</span>
                </span>
              </label>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" loading={submitting} disabled={loading} onClick={handleSave}>
            Save Store Access
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
