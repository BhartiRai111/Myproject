import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Role } from '../types/user';

interface Props {
  /** Additional roles allowed beyond ADMIN/STORE_MANAGER, e.g. the module-scoped roles from Step 5. */
  extraRoles?: Role[];
}

export default function ManagerRoute({ extraRoles = [] }: Props) {
  const { user } = useAuth();
  const allowed: Role[] = ['ADMIN', 'STORE_MANAGER', ...extraRoles];

  if (!user || !allowed.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <Outlet />;
}
