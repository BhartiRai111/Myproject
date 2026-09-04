import { useCallback, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * Lets a form's Back/Cancel actions navigate away immediately when nothing has
 * changed, but prompt for confirmation when the current form state no longer
 * matches the snapshot taken when the form was loaded (via markClean).
 */
export function useUnsavedChangesGuard(isDirty: () => boolean) {
  const navigate = useNavigate();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const pendingPath = useRef<string | null>(null);

  const guardedNavigate = useCallback(
    (path: string) => {
      if (isDirty()) {
        pendingPath.current = path;
        setConfirmOpen(true);
      } else {
        navigate(path);
      }
    },
    [isDirty, navigate]
  );

  const confirmLeave = useCallback(() => {
    setConfirmOpen(false);
    if (pendingPath.current) {
      navigate(pendingPath.current);
      pendingPath.current = null;
    }
  }, [navigate]);

  const cancelLeave = useCallback(() => {
    setConfirmOpen(false);
    pendingPath.current = null;
  }, []);

  return { guardedNavigate, confirmOpen, confirmLeave, cancelLeave };
}
