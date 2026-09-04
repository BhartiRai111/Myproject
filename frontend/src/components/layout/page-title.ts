export function getPageTitle(pathname: string): { title: string; parent?: string } {
  if (pathname === '/dashboard') return { title: 'Dashboard' };
  if (pathname === '/users') return { title: 'User Management' };

  if (pathname === '/purchases') return { title: 'Purchases' };
  if (pathname === '/purchases/new') return { title: 'Create Purchase', parent: 'Purchases' };
  if (/^\/purchases\/\d+\/edit$/.test(pathname)) return { title: 'Edit Purchase', parent: 'Purchases' };

  if (pathname === '/sales') return { title: 'Sales' };

  if (pathname === '/sales/orders') return { title: 'Sales Orders', parent: 'Sales' };
  if (pathname === '/sales/orders/new') return { title: 'New Sales Order', parent: 'Sales Orders' };
  if (/^\/sales\/orders\/\d+\/edit$/.test(pathname)) return { title: 'Edit Sales Order', parent: 'Sales Orders' };
  if (/^\/sales\/orders\/\d+$/.test(pathname)) return { title: 'Sales Order Detail', parent: 'Sales Orders' };

  if (pathname === '/sales/bills') return { title: 'Sales Bills', parent: 'Sales' };
  if (pathname === '/sales/bills/new') return { title: 'New Sales Bill', parent: 'Sales Bills' };
  if (/^\/sales\/bills\/\d+\/edit$/.test(pathname)) return { title: 'Edit Sales Bill', parent: 'Sales Bills' };
  if (/^\/sales\/bills\/\d+$/.test(pathname)) return { title: 'Sales Bill Detail', parent: 'Sales Bills' };

  if (pathname === '/sales/receipts') return { title: 'Receipt Entries', parent: 'Sales' };
  if (pathname === '/sales/receipts/new') return { title: 'New Receipt', parent: 'Receipt Entries' };
  if (/^\/sales\/receipts\/\d+$/.test(pathname)) return { title: 'Receipt Detail', parent: 'Receipt Entries' };

  const known = ['products', 'inventory', 'suppliers', 'customers', 'payments', 'reports'];
  const bare = pathname.replace(/^\//, '');
  if (known.includes(bare)) {
    return { title: bare.charAt(0).toUpperCase() + bare.slice(1) };
  }

  return { title: 'StoreHub' };
}
