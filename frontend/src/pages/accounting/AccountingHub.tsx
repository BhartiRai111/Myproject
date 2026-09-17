import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Wallet,
  Landmark,
  Users,
  Truck,
  TrendingUp,
  TrendingDown,
  BookText,
  BookOpen,
  Scale,
  Receipt,
  FileBarChart,
  BarChart3,
  ShieldCheck,
  ArrowRight,
  CreditCard,
  CalendarCheck,
  Building2,
} from 'lucide-react';
import { accountingReportApi } from '../../api/accountingApi';
import { salesSummaryApi } from '../../api/salesSummaryApi';
import { purchaseSummaryApi } from '../../api/purchaseSummaryApi';
import { AccountingDashboardSummary } from '../../types/accounting';
import { useAuth } from '../../context/AuthContext';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';

const money = (n: number) => `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export default function AccountingHub() {
  const navigate = useNavigate();
  const { myStores } = useAuth();
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
    {
      key: 'expenses',
      title: 'Expenses',
      description: 'Record operational expenses — rent, electricity, transport — posted through the same accounting engine.',
      icon: TrendingDown,
      path: '/accounting/expenses',
    },
    {
      key: 'cash-transactions',
      title: 'Cash Management',
      description: 'Cash In / Cash Out entries — reflected directly in the Cash Book via the same accounting engine.',
      icon: Wallet,
      path: '/accounting/cash-transactions',
    },
    {
      key: 'payment-methods',
      title: 'Payment Methods',
      description: 'Manage payment methods available across POS, Sales, Purchases, Expenses, and Cash entries.',
      icon: CreditCard,
      path: '/accounting/payment-methods',
    },
    {
      key: 'day-closing',
      title: 'Day Closing',
      description: 'Reconcile expected vs. actual cash for a date, with an audited difference reason when they disagree.',
      icon: CalendarCheck,
      path: '/accounting/day-closing',
    },
    {
      key: 'day-book',
      title: 'Day Book',
      description: 'Every POSTED voucher for a date range — Kacchi transactions are included.',
      icon: BookOpen,
      path: '/accounting/reports/day-book',
    },
    {
      key: 'account-ledger',
      title: 'Account Ledger',
      description: 'Running-balance statement for any account in the Chart of Accounts.',
      icon: BookOpen,
      path: '/accounting/reports/account-ledger',
    },
    {
      key: 'cash-book',
      title: 'Cash Book',
      description: 'Opening cash, receipts, payments, and closing cash for a date range.',
      icon: Wallet,
      path: '/accounting/reports/cash-book',
    },
    {
      key: 'bank-book',
      title: 'Bank Book',
      description: 'Opening bank balance, receipts, payments, and closing balance for a date range.',
      icon: Landmark,
      path: '/accounting/reports/bank-book',
    },
    {
      key: 'party-ledger',
      title: 'Party Ledger',
      description: 'Unified running-balance statement for one customer or supplier.',
      icon: Users,
      path: '/accounting/reports/party-ledger',
    },
    {
      key: 'receivable',
      title: 'Receivable',
      description: 'Outstanding customer balances: opening + credit sales − receipts.',
      icon: Users,
      path: '/accounting/reports/receivable',
    },
    {
      key: 'payable',
      title: 'Payable',
      description: 'Outstanding supplier balances: opening + purchases − payments.',
      icon: Truck,
      path: '/accounting/reports/payable',
    },
    {
      key: 'outstanding',
      title: 'Outstanding Bills',
      description: 'Bill-wise outstanding with ageing buckets, for customers and suppliers.',
      icon: Receipt,
      path: '/accounting/reports/outstanding',
    },
    {
      key: 'profit-loss',
      title: 'Profit & Loss',
      description: 'Income vs. Expenses from the Chart of Accounts, for a date range.',
      icon: FileBarChart,
      path: '/accounting/reports/profit-loss',
    },
    {
      key: 'balance-sheet',
      title: 'Balance Sheet',
      description: 'Assets = Liabilities + Equity, as of a date.',
      icon: Scale,
      path: '/accounting/reports/balance-sheet',
    },
    {
      key: 'account-summary',
      title: 'Account Summary',
      description: 'Opening, debit/credit, and closing balance for every active account.',
      icon: BarChart3,
      path: '/accounting/reports/account-summary',
    },
    {
      key: 'expense-summary',
      title: 'Expense Summary',
      description: 'Every expense account grouped with its posted vouchers.',
      icon: TrendingDown,
      path: '/accounting/reports/expense-summary',
    },
    {
      key: 'expense-analysis',
      title: 'Expense Analysis',
      description: 'Category-wise, payment-method-wise, party-wise, and GST/ITC-wise expense totals.',
      icon: BarChart3,
      path: '/accounting/reports/expense-analysis',
    },
    {
      key: 'income-summary',
      title: 'Income Summary',
      description: 'Every income account grouped with its posted vouchers.',
      icon: TrendingUp,
      path: '/accounting/reports/income-summary',
    },
    {
      key: 'health-check',
      title: 'Accounting Health Check',
      description: 'Journal balance, duplicate posting, orphan journals, and ledger reconciliation.',
      icon: ShieldCheck,
      path: '/accounting/reports/health-check',
    },
    ...(myStores.length > 1
      ? [
          {
            key: 'store-comparison',
            title: 'Store Comparison',
            description: 'Sales, purchases and stock position side-by-side across every store you can access.',
            icon: Building2,
            path: '/accounting/reports/store-comparison',
          },
        ]
      : []),
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
