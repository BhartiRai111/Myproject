export function getPageTitle(pathname: string): { title: string; parent?: string } {
  if (pathname === '/dashboard') return { title: 'Dashboard' };
  if (pathname === '/users') return { title: 'User Management' };

  if (pathname === '/purchases') return { title: 'Purchases' };

  if (pathname === '/purchases/orders') return { title: 'Purchase Orders', parent: 'Purchases' };
  if (pathname === '/purchases/orders/new') return { title: 'New Purchase Order', parent: 'Purchase Orders' };
  if (/^\/purchases\/orders\/\d+\/edit$/.test(pathname)) return { title: 'Edit Purchase Order', parent: 'Purchase Orders' };
  if (/^\/purchases\/orders\/\d+$/.test(pathname)) return { title: 'Purchase Order Detail', parent: 'Purchase Orders' };

  if (pathname === '/purchases/bills') return { title: 'Purchase Bills', parent: 'Purchases' };
  if (pathname === '/purchases/bills/new') return { title: 'New Purchase Bill', parent: 'Purchase Bills' };
  if (/^\/purchases\/bills\/\d+\/edit$/.test(pathname)) return { title: 'Edit Purchase Bill', parent: 'Purchase Bills' };
  if (/^\/purchases\/bills\/\d+$/.test(pathname)) return { title: 'Purchase Bill Detail', parent: 'Purchase Bills' };

  if (pathname === '/purchases/payments') return { title: 'Payment Entries', parent: 'Purchases' };
  if (pathname === '/purchases/payments/new') return { title: 'New Payment', parent: 'Payment Entries' };
  if (/^\/purchases\/payments\/\d+$/.test(pathname)) return { title: 'Payment Detail', parent: 'Payment Entries' };

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
