import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wallet, Landmark, Users, Truck, TrendingUp, TrendingDown, BookText, Scale, ArrowRight } from 'lucide-react';
import { accountingReportApi } from '../../api/accountingApi';
import { salesSummaryApi } from '../../api/salesSummaryApi';
import { purchaseSummaryApi } from '../../api/purchaseSummaryApi';
import { AccountingDashboardSummary } from '../../types/accounting';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export default function AccountingHub() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState<AccountingDashboardSummary | null>(null);
  const [totalSales, setTotalSales] = useState<number | null>(null);
  const [totalPurchase, setTotalPurchase] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([accountingReportApi.dashboard(), salesSummaryApi.get(), purchaseSummaryApi.get()])
      .then(([dashboardRes, salesRes, purchaseRes]) => {
        setSummary(dashboardRes.data);
        setTotalSales(salesRes.data.todaysSales);
        setTotalPurchase(purchaseRes.data.todaysPurchases);
      })
      .finally(() => setLoading(false));
  }, []);

  const statCards = [
    { label: 'Cash Balance', value: summary ? money(summary.cashBalance) : undefined, icon: Wallet },
    { label: 'Bank Balance', value: summary ? money(summary.bankBalance) : undefined, icon: Landmark },
    { label: 'Customer Receivable', value: summary ? money(summary.receivableBalance) : undefined, icon: Users },
    { label: 'Supplier Payable', value: summary ? money(summary.payableBalance) : undefined, icon: Truck },
    { label: "Today's Sales", value: totalSales !== null ? money(totalSales) : undefined, icon: TrendingUp },
    { label: "Today's Purchases", value: totalPurchase !== null ? money(totalPurchase) : undefined, icon: TrendingDown },
  ];

  const sections = [
    {
      key: 'accounts',
      title: 'Chart of Accounts',
      description: 'View and manage the ledger accounts financial transactions post to.',
      icon: Landmark,
      path: '/accounting/accounts',
    },
    {
      key: 'journals',
      title: 'Journals',
      description: 'Browse every posted journal — automatic and manual — with full debit/credit lines.',
      icon: BookText,
      path: '/accounting/journals',
    },
    {
      key: 'trial-balance',
      title: 'Trial Balance',
      description: 'Account-wise debit and credit balances, verifying the engine stays balanced.',
      icon: Scale,
      path: '/accounting/trial-balance',
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Accounting" description="Double-entry accounting engine: Chart of Accounts, Journals, and Trial Balance." />

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        {statCards.map((card) => (
          <Card key={card.label}>
            <CardContent className="flex flex-col gap-2 p-4">
              <div className="flex items-center gap-2 text-muted-foreground">
                <card.icon className="h-4 w-4" />
                <span className="text-xs font-medium">{card.label}</span>
              </div>
              {loading ? <Skeleton className="h-6 w-20" /> : <span className="text-lg font-bold">{card.value ?? '₹0.00'}</span>}
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
