import { Nav } from 'react-bootstrap';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Role } from '../../types/user';

interface NavItem {
  label: string;
  path: string;
  enabled: boolean;
  allowedRoles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', enabled: true },
  { label: 'Users', path: '/users', enabled: true, allowedRoles: ['ADMIN'] },
  { label: 'Products', path: '/products', enabled: false },
  { label: 'Inventory', path: '/inventory', enabled: false },
  { label: 'Suppliers', path: '/suppliers', enabled: false },
  { label: 'Purchases', path: '/purchases', enabled: true, allowedRoles: ['ADMIN', 'STORE_MANAGER'] },
  { label: 'Sales', path: '/sales', enabled: false },
  { label: 'Customers', path: '/customers', enabled: false },
  { label: 'Payments', path: '/payments', enabled: false },
  { label: 'Reports', path: '/reports', enabled: false },
];

export default function Sidebar() {
  const { user } = useAuth();

  const items = NAV_ITEMS.filter((item) => !item.allowedRoles || (user && item.allowedRoles.includes(user.role)));

  return (
    <div className="sh-sidebar p-3" style={{ width: 220 }}>
      <Nav className="flex-column">
        {items.map((item) =>
          item.enabled ? (
            <Nav.Link key={item.path} as={NavLink} to={item.path} end>
              {item.label}
            </Nav.Link>
          ) : (
            <Nav.Link key={item.path} disabled title="Coming soon">
              {item.label} <span className="text-muted small">(Coming soon)</span>
            </Nav.Link>
          )
        )}
      </Nav>
    </div>
  );
}
