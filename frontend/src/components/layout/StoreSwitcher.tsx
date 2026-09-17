import { useState } from 'react';
import { toast } from 'sonner';
import { Check, ChevronsUpDown, Store as StoreIcon } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { parseApiError } from '../../utils/apiError';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

/**
 * Current-store selector (Multi-Store spec sections 12, 45) — only rendered when the user has
 * more than one accessible store; a single-store user never needs to choose. Switching is
 * backend-validated (StoreAccessService) on every call, so this is a convenience UI, never
 * the source of authorization truth.
 */
export default function StoreSwitcher() {
  const { user, myStores, switchStore } = useAuth();
  const [switching, setSwitching] = useState(false);

  if (myStores.length <= 1) {
    return null;
  }

  const handleSwitch = async (storeId: number) => {
    if (storeId === user?.currentStoreId) return;
    setSwitching(true);
    try {
      await switchStore(storeId);
      toast.success('Current store switched');
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to switch store').message);
    } finally {
      setSwitching(false);
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="hidden items-center gap-2 sm:flex" loading={switching}>
          <StoreIcon className="h-4 w-4" />
          <span className="max-w-[10rem] truncate">{user?.currentStoreName || 'Select Store'}</span>
          <ChevronsUpDown className="h-3.5 w-3.5 text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">Switch Store</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {myStores.map((store) => (
          <DropdownMenuItem key={store.id} onClick={() => handleSwitch(store.id)} className="flex items-center justify-between gap-2">
            <div className="min-w-0">
              <div className="truncate text-sm font-medium">{store.storeName}</div>
              <div className="text-xs text-muted-foreground">{store.storeCode}</div>
            </div>
            {store.id === user?.currentStoreId && <Check className="h-4 w-4 shrink-0 text-primary" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
