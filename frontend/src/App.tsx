import { Navigate, Route, Routes } from 'react-router-dom';
import AdminRoute from './components/AdminRoute';
import ManagerRoute from './components/ManagerRoute';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/layout/MainLayout';
import { AuthProvider } from './context/AuthContext';
import ComingSoon from './pages/ComingSoon';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import ProductForm from './pages/ProductForm';
import Products from './pages/Products';
import PurchaseForm from './pages/PurchaseForm';
import Purchases from './pages/Purchases';
import Register from './pages/Register';
import SaleForm from './pages/SaleForm';
import Sales from './pages/Sales';
import SupplierForm from './pages/SupplierForm';
import Suppliers from './pages/Suppliers';
import Users from './pages/Users';

const DISABLED_MODULES = ['inventory', 'customers', 'payments', 'reports'];

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

            <Route element={<ManagerRoute />}>
              <Route path="/purchases" element={<Purchases />} />
              <Route path="/purchases/new" element={<PurchaseForm />} />
              <Route path="/purchases/:id/edit" element={<PurchaseForm />} />
            </Route>

            <Route path="/sales" element={<Sales />} />
            <Route path="/sales/new" element={<SaleForm />} />
            <Route element={<ManagerRoute />}>
              <Route path="/sales/:id/edit" element={<SaleForm />} />
            </Route>

            <Route path="/products" element={<Products />} />
            <Route element={<ManagerRoute />}>
              <Route path="/products/new" element={<ProductForm />} />
              <Route path="/products/:id/edit" element={<ProductForm />} />
            </Route>

            <Route element={<ManagerRoute />}>
              <Route path="/suppliers" element={<Suppliers />} />
              <Route path="/suppliers/new" element={<SupplierForm />} />
              <Route path="/suppliers/:id/edit" element={<SupplierForm />} />
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
