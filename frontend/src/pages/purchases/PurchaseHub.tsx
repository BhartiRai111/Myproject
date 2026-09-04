import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, FileText, Wallet, ArrowRight, Clock, IndianRupee, TrendingUp } from 'lucide-react';
import { purchaseSummaryApi } from '../../api/purchaseSummaryApi';
import { PurchaseSummary } from '../../types/payment';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export default function PurchaseHub() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState<PurchaseSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    purchaseSummaryApi
      .get()
      .then((res) => setSummary(res.data))
      .finally(() => setLoading(false));
  }, []);

  const statCards = [
    { label: 'Total Purchase Orders', value: summary?.totalPurchaseOrders, icon: ClipboardList },
    { label: 'Pending Orders', value: summary?.pendingOrders, icon: Clock },
    { label: 'Total Purchase Bills', value: summary?.totalPurchaseBills, icon: FileText },
    { label: 'Total Payables', value: summary ? money(summary.totalPayables) : undefined, icon: Wallet },
    { label: "Today's Purchases", value: summary ? money(summary.todaysPurchases) : undefined, icon: TrendingUp },
  ];

  const sections = [
    {
      key: 'orders',
      title: 'Purchase Order',
      description: 'Create and track supplier orders before they are billed.',
      icon: ClipboardList,
      path: '/purchases/orders',
    },
    {
      key: 'bills',
      title: 'Purchase Bill',
      description: 'Create GST or Non-GST bills, with automatic stock and account updates.',
      icon: FileText,
      path: '/purchases/bills',
    },
    {
      key: 'payments',
      title: 'Payment Entry',
      description: 'Record payments made against outstanding purchase bills.',
      icon: IndianRupee,
      path: '/purchases/payments',
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Purchases" description="Purchase Orders, Purchase Bills, and Payment Entries in one place." />

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {statCards.map((card) => (
          <Card key={card.label}>
            <CardContent className="flex flex-col gap-2 p-4">
              <div className="flex items-center gap-2 text-muted-foreground">
                <card.icon className="h-4 w-4" />
                <span className="text-xs font-medium">{card.label}</span>
              </div>
              {loading ? (
                <Skeleton className="h-6 w-16" />
              ) : (
                <span className="text-xl font-bold">{card.value ?? 0}</span>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {sections.map((section) => (
          <Card key={section.key} className="flex flex-col">
            <CardContent className="flex flex-1 flex-col gap-3 p-5">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <section.icon className="h-5 w-5" />
              </div>
              <p className="font-semibold">{section.title}</p>
              <p className="flex-1 text-sm text-muted-foreground">{section.description}</p>
              <Button variant="outline" className="w-full" onClick={() => navigate(section.path)}>
                Open <ArrowRight className="h-4 w-4" />
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
