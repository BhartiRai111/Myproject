import { Navigate, Route, Routes } from 'react-router-dom';
import AdminRoute from './components/AdminRoute';
import ManagerRoute from './components/ManagerRoute';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/layout/MainLayout';
import { AuthProvider } from './context/AuthContext';
import ComingSoon from './pages/ComingSoon';
import Dashboard from './pages/Dashboard';
import InventoryPage from './pages/Inventory';
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
import MastersDashboard from './pages/MastersDashboard';
import CurrencyMaster from './pages/masters/CurrencyMaster';
import CountryMaster from './pages/masters/CountryMaster';
import StateMaster from './pages/masters/StateMaster';
import CityMaster from './pages/masters/CityMaster';
import ZoneMaster from './pages/masters/ZoneMaster';
import NationalityMaster from './pages/masters/NationalityMaster';
import UnitMaster from './pages/masters/UnitMaster';
import ItemGroupMaster from './pages/masters/ItemGroupMaster';
import HsnMaster from './pages/masters/HsnMaster';
import EmployeeMaster from './pages/masters/EmployeeMaster';
import PartyMaster from './pages/masters/PartyMaster';

const DISABLED_MODULES = ['customers', 'payments', 'reports'];

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

            <Route path="/inventory" element={<InventoryPage />} />

            <Route element={<ManagerRoute />}>
              <Route path="/suppliers" element={<Suppliers />} />
              <Route path="/suppliers/new" element={<SupplierForm />} />
              <Route path="/suppliers/:id/edit" element={<SupplierForm />} />
            </Route>

            <Route element={<ManagerRoute />}>
              <Route path="/masters" element={<MastersDashboard />} />
              <Route path="/masters/currencies" element={<CurrencyMaster />} />
              <Route path="/masters/countries" element={<CountryMaster />} />
              <Route path="/masters/states" element={<StateMaster />} />
              <Route path="/masters/cities" element={<CityMaster />} />
              <Route path="/masters/zones" element={<ZoneMaster />} />
              <Route path="/masters/nationalities" element={<NationalityMaster />} />
              <Route path="/masters/units" element={<UnitMaster />} />
              <Route path="/masters/item-groups" element={<ItemGroupMaster />} />
              <Route path="/masters/hsn" element={<HsnMaster />} />
              <Route path="/masters/employees" element={<EmployeeMaster />} />
              <Route path="/masters/parties" element={<PartyMaster />} />
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
