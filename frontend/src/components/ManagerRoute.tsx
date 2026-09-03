import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ManagerRoute() {
  const { user } = useAuth();

  if (user?.role !== 'ADMIN' && user?.role !== 'STORE_MANAGER') {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
