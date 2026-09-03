import { Navigate, Route, Routes } from 'react-router-dom';
import AdminRoute from './components/AdminRoute';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/layout/MainLayout';
import { AuthProvider } from './context/AuthContext';
import ComingSoon from './pages/ComingSoon';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Register from './pages/Register';
import Users from './pages/Users';

const DISABLED_MODULES = [
  'products',
  'inventory',
  'suppliers',
  'purchases',
  'sales',
  'customers',
  'payments',
  'reports',
];

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />

            <Route element={<AdminRoute />}>
              <Route path="/users" element={<Users />} />
            </Route>

            {DISABLED_MODULES.map((path) => (
              <Route key={path} path={`/${path}`} element={<ComingSoon />} />
            ))}
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}
