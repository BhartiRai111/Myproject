export function getPageTitle(pathname: string): { title: string; parent?: string } {
  if (pathname === '/dashboard') return { title: 'Dashboard' };
  if (pathname === '/users') return { title: 'User Management' };

  if (pathname === '/purchases') return { title: 'Purchases' };
  if (pathname === '/purchases/new') return { title: 'Create Purchase', parent: 'Purchases' };
  if (/^\/purchases\/\d+\/edit$/.test(pathname)) return { title: 'Edit Purchase', parent: 'Purchases' };

  if (pathname === '/sales') return { title: 'Sales' };
  if (pathname === '/sales/new') return { title: 'Create Sale', parent: 'Sales' };
  if (/^\/sales\/\d+\/edit$/.test(pathname)) return { title: 'Edit Sale', parent: 'Sales' };

  const known = ['products', 'inventory', 'suppliers', 'customers', 'payments', 'reports'];
  const bare = pathname.replace(/^\//, '');
  if (known.includes(bare)) {
    return { title: bare.charAt(0).toUpperCase() + bare.slice(1) };
  }

  return { title: 'StoreHub' };
}
