import { Navigate, Route, Routes } from 'react-router-dom';
import AdminRoute from './components/AdminRoute';
import ManagerRoute from './components/ManagerRoute';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/layout/MainLayout';
import { AuthProvider } from './context/AuthContext';
import ComingSoon from './pages/ComingSoon';
import Dashboard from './pages/Dashboard';
import AlertsCenter from './pages/AlertsCenter';
import InventoryPage from './pages/Inventory';
import Login from './pages/Login';
import ProductForm from './pages/ProductForm';
import Products from './pages/Products';
import Register from './pages/Register';
import PurchaseHub from './pages/purchases/PurchaseHub';
import PurchaseOrders from './pages/purchases/PurchaseOrders';
import PurchaseOrderForm from './pages/purchases/PurchaseOrderForm';
import PurchaseOrderDetail from './pages/purchases/PurchaseOrderDetail';
import PurchaseBills from './pages/purchases/PurchaseBills';
import PurchaseBillForm from './pages/purchases/PurchaseBillForm';
import PurchaseBillDetail from './pages/purchases/PurchaseBillDetail';
import KacchiPurchases from './pages/purchases/KacchiPurchases';
import KacchiPurchaseForm from './pages/purchases/KacchiPurchaseForm';
import KacchiPurchaseDetail from './pages/purchases/KacchiPurchaseDetail';
import Payments from './pages/purchases/Payments';
import PaymentForm from './pages/purchases/PaymentForm';
import PaymentDetail from './pages/purchases/PaymentDetail';
import SalesHub from './pages/sales/SalesHub';
import Pos from './pages/sales/Pos';
import SalesOrders from './pages/sales/SalesOrders';
import SalesOrderForm from './pages/sales/SalesOrderForm';
import SalesOrderDetail from './pages/sales/SalesOrderDetail';
import SalesBills from './pages/sales/SalesBills';
import SalesBillForm from './pages/sales/SalesBillForm';
import SalesBillDetail from './pages/sales/SalesBillDetail';
import KacchiSales from './pages/sales/KacchiSales';
import KacchiSaleForm from './pages/sales/KacchiSaleForm';
import KacchiSaleDetail from './pages/sales/KacchiSaleDetail';
import Receipts from './pages/sales/Receipts';
import ReceiptForm from './pages/sales/ReceiptForm';
import ReceiptDetail from './pages/sales/ReceiptDetail';
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
import AccountingHub from './pages/accounting/AccountingHub';
import Expenses from './pages/accounting/Expenses';
import ExpenseForm from './pages/accounting/ExpenseForm';
import ExpenseDetail from './pages/accounting/ExpenseDetail';
import CashTransactions from './pages/accounting/CashTransactions';
import CashTransactionForm from './pages/accounting/CashTransactionForm';
import CashTransactionDetail from './pages/accounting/CashTransactionDetail';
import PaymentMethods from './pages/accounting/PaymentMethods';
import DayClosingPage from './pages/accounting/DayClosingPage';
import AccountMaster from './pages/accounting/AccountMaster';
import Journals from './pages/accounting/Journals';
import JournalEntryForm from './pages/accounting/JournalEntryForm';
import TrialBalance from './pages/accounting/TrialBalance';
import DayBook from './pages/accounting/reports/DayBook';
import AccountLedger from './pages/accounting/reports/AccountLedger';
import CashBook from './pages/accounting/reports/CashBook';
import BankBook from './pages/accounting/reports/BankBook';
import PartyLedger from './pages/accounting/reports/PartyLedger';
import Receivable from './pages/accounting/reports/Receivable';
import Payable from './pages/accounting/reports/Payable';
import Outstanding from './pages/accounting/reports/Outstanding';
import ProfitLoss from './pages/accounting/reports/ProfitLoss';
import BalanceSheet from './pages/accounting/reports/BalanceSheet';
import AccountSummary from './pages/accounting/reports/AccountSummary';
import ExpenseSummary from './pages/accounting/reports/ExpenseSummary';
import IncomeSummary from './pages/accounting/reports/IncomeSummary';
import HealthCheck from './pages/accounting/reports/HealthCheck';
import GstReportsHub from './pages/gst/GstReportsHub';
import Gstr1Report from './pages/gst/Gstr1Report';
import PurchaseGstReport from './pages/gst/PurchaseGstReport';
import Gstr3bSummary from './pages/gst/Gstr3bSummary';
import OutputGstReport from './pages/gst/OutputGstReport';
import InputGstReport from './pages/gst/InputGstReport';
import HsnSummaryReport from './pages/gst/HsnSummaryReport';
import TaxRateSummaryReport from './pages/gst/TaxRateSummaryReport';
import GstLiabilityReport from './pages/gst/GstLiabilityReport';
import GstReconciliation from './pages/gst/GstReconciliation';
import FinancialYears from './pages/admin/FinancialYears';
import AuditTrail from './pages/admin/AuditTrail';
import CreditNotes from './pages/sales/CreditNotes';
import CreditNoteForm from './pages/sales/CreditNoteForm';
import CreditNoteDetail from './pages/sales/CreditNoteDetail';
import DebitNotes from './pages/purchases/DebitNotes';
import DebitNoteForm from './pages/purchases/DebitNoteForm';
import DebitNoteDetail from './pages/purchases/DebitNoteDetail';

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
            <Route path="/alerts" element={<AlertsCenter />} />

            <Route element={<AdminRoute />}>
              <Route path="/users" element={<Users />} />
              <Route path="/admin/financial-years" element={<FinancialYears />} />
              <Route path="/admin/audit-trail" element={<AuditTrail />} />
            </Route>

            <Route element={<ManagerRoute />}>
              <Route path="/purchases" element={<PurchaseHub />} />

              <Route path="/purchases/orders" element={<PurchaseOrders />} />
              <Route path="/purchases/orders/new" element={<PurchaseOrderForm />} />
              <Route path="/purchases/orders/:id" element={<PurchaseOrderDetail />} />
              <Route path="/purchases/orders/:id/edit" element={<PurchaseOrderForm />} />

              <Route path="/purchases/bills" element={<PurchaseBills />} />
              <Route path="/purchases/bills/new" element={<PurchaseBillForm />} />
              <Route path="/purchases/bills/:id" element={<PurchaseBillDetail />} />
              <Route path="/purchases/bills/:id/edit" element={<PurchaseBillForm />} />

              <Route path="/purchases/payments" element={<Payments />} />
              <Route path="/purchases/payments/new" element={<PaymentForm />} />
              <Route path="/purchases/payments/:id" element={<PaymentDetail />} />

              <Route path="/purchases/kacchi" element={<KacchiPurchases />} />
              <Route path="/purchases/kacchi/new" element={<KacchiPurchaseForm />} />
              <Route path="/purchases/kacchi/:id" element={<KacchiPurchaseDetail />} />
              <Route path="/purchases/kacchi/:id/edit" element={<KacchiPurchaseForm />} />

              <Route path="/purchases/debit-notes" element={<DebitNotes />} />
              <Route path="/purchases/debit-notes/new" element={<DebitNoteForm />} />
              <Route path="/purchases/debit-notes/:id" element={<DebitNoteDetail />} />
            </Route>

            <Route path="/sales" element={<SalesHub />} />
            <Route path="/sales/pos" element={<Pos />} />

            <Route path="/sales/orders" element={<SalesOrders />} />
            <Route path="/sales/orders/new" element={<SalesOrderForm />} />
            <Route path="/sales/orders/:id" element={<SalesOrderDetail />} />
            <Route element={<ManagerRoute />}>
              <Route path="/sales/orders/:id/edit" element={<SalesOrderForm />} />
            </Route>

            <Route path="/sales/bills" element={<SalesBills />} />
            <Route path="/sales/bills/new" element={<SalesBillForm />} />
            <Route path="/sales/bills/:id" element={<SalesBillDetail />} />
            <Route element={<ManagerRoute />}>
              <Route path="/sales/bills/:id/edit" element={<SalesBillForm />} />
            </Route>

            <Route path="/sales/receipts" element={<Receipts />} />
            <Route path="/sales/receipts/new" element={<ReceiptForm />} />
            <Route path="/sales/receipts/:id" element={<ReceiptDetail />} />

            <Route path="/sales/kacchi" element={<KacchiSales />} />
            <Route path="/sales/kacchi/new" element={<KacchiSaleForm />} />
            <Route path="/sales/kacchi/:id" element={<KacchiSaleDetail />} />
            <Route element={<ManagerRoute />}>
              <Route path="/sales/kacchi/:id/edit" element={<KacchiSaleForm />} />
            </Route>

            <Route path="/sales/credit-notes" element={<CreditNotes />} />
            <Route path="/sales/credit-notes/new" element={<CreditNoteForm />} />
            <Route path="/sales/credit-notes/:id" element={<CreditNoteDetail />} />

            <Route element={<ManagerRoute />}>
              <Route path="/accounting" element={<AccountingHub />} />
              <Route path="/accounting/accounts" element={<AccountMaster />} />
              <Route path="/accounting/journals" element={<Journals />} />
              <Route path="/accounting/trial-balance" element={<TrialBalance />} />
              <Route path="/accounting/expenses" element={<Expenses />} />
              <Route path="/accounting/expenses/new" element={<ExpenseForm />} />
              <Route path="/accounting/expenses/:id" element={<ExpenseDetail />} />
              <Route path="/accounting/cash-transactions" element={<CashTransactions />} />
              <Route path="/accounting/cash-transactions/new" element={<CashTransactionForm />} />
              <Route path="/accounting/cash-transactions/:id" element={<CashTransactionDetail />} />
              <Route path="/accounting/payment-methods" element={<PaymentMethods />} />
              <Route path="/accounting/day-closing" element={<DayClosingPage />} />
              <Route element={<AdminRoute />}>
                <Route path="/accounting/journals/new" element={<JournalEntryForm />} />
              </Route>
              <Route path="/accounting/reports/day-book" element={<DayBook />} />
              <Route path="/accounting/reports/account-ledger" element={<AccountLedger />} />
              <Route path="/accounting/reports/cash-book" element={<CashBook />} />
              <Route path="/accounting/reports/bank-book" element={<BankBook />} />
              <Route path="/accounting/reports/party-ledger" element={<PartyLedger />} />
              <Route path="/accounting/reports/receivable" element={<Receivable />} />
              <Route path="/accounting/reports/payable" element={<Payable />} />
              <Route path="/accounting/reports/outstanding" element={<Outstanding />} />
              <Route path="/accounting/reports/profit-loss" element={<ProfitLoss />} />
              <Route path="/accounting/reports/balance-sheet" element={<BalanceSheet />} />
              <Route path="/accounting/reports/account-summary" element={<AccountSummary />} />
              <Route path="/accounting/reports/expense-summary" element={<ExpenseSummary />} />
              <Route path="/accounting/reports/income-summary" element={<IncomeSummary />} />
              <Route path="/accounting/reports/health-check" element={<HealthCheck />} />
            </Route>

            <Route element={<ManagerRoute />}>
              <Route path="/gst-reports" element={<GstReportsHub />} />
              <Route path="/gst-reports/gstr1" element={<Gstr1Report />} />
              <Route path="/gst-reports/purchase" element={<PurchaseGstReport />} />
              <Route path="/gst-reports/gstr3b" element={<Gstr3bSummary />} />
              <Route path="/gst-reports/output" element={<OutputGstReport />} />
              <Route path="/gst-reports/input" element={<InputGstReport />} />
              <Route path="/gst-reports/hsn" element={<HsnSummaryReport />} />
              <Route path="/gst-reports/tax-rate" element={<TaxRateSummaryReport />} />
              <Route path="/gst-reports/liability" element={<GstLiabilityReport />} />
              <Route path="/gst-reports/reconciliation" element={<GstReconciliation />} />
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
