import {
  LayoutDashboard,
  Users,
  Package,
  Boxes,
  Truck,
  ShoppingCart,
  Receipt,
  Contact,
  CreditCard,
  BarChart3,
  Landmark,
  BookText,
  FileSpreadsheet,
  CalendarRange,
  ShieldAlert,
  type LucideIcon,
} from 'lucide-react';
import { Role } from '@/types/user';

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  enabled: boolean;
  allowedRoles?: Role[];
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard, enabled: true },
  { label: 'Users', path: '/users', icon: Users, enabled: true, allowedRoles: ['ADMIN'] },
  { label: 'Products', path: '/products', icon: Package, enabled: true },
  { label: 'Inventory', path: '/inventory', icon: Boxes, enabled: true },
  { label: 'Suppliers', path: '/suppliers', icon: Truck, enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'Purchases', path: '/purchases', icon: ShoppingCart, enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'Sales', path: '/sales', icon: Receipt, enabled: true },
  { label: 'Accounting', path: '/accounting', icon: BookText, enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'GST Reports', path: '/gst-reports', icon: FileSpreadsheet, enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'Masters', path: '/masters', icon: Landmark, enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'Financial Years', path: '/admin/financial-years', icon: CalendarRange, enabled: true, allowedRoles: ['ADMIN'] },
  { label: 'Audit Trail', path: '/admin/audit-trail', icon: ShieldAlert, enabled: true, allowedRoles: ['ADMIN'] },
  { label: 'Customers', path: '/customers', icon: Contact, enabled: false },
  { label: 'Payments', path: '/payments', icon: CreditCard, enabled: false },
  { label: 'Reports', path: '/reports', icon: BarChart3, enabled: false },
];
