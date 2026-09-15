package com.storehub.service;

import com.storehub.dto.AccountingHealthCheckResponse;
import com.storehub.dto.BalanceSheetResponse;
import com.storehub.dto.CashBankBookResponse;
import com.storehub.dto.DayBookResponse;
import com.storehub.dto.OutstandingBillReportResponse;
import com.storehub.dto.PaymentRequest;
import com.storehub.dto.ProfitLossResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.ReceiptRequest;
import com.storehub.dto.ReceivablePayableResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.dto.TrialBalanceResponse;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.GstType;
import com.storehub.entity.HealthCheckStatus;
import com.storehub.entity.Inventory;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import com.storehub.entity.VoucherType;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 test matrix: Accounting Reports & Financial Statements. Every
 * report under test here must read the posted accounting journal (never a
 * second, independently calculated figure) — see each test's comment for
 * which spec scenario it covers. @Transactional rolls back automatically.
 */
@SpringBootTest
@Transactional
class AccountingReportsTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AccountingReportService accountingReportService;
    @Autowired
    private CashBankBookService cashBankBookService;
    @Autowired
    private ReceivablePayableService receivablePayableService;
    @Autowired
    private OutstandingBillService outstandingBillService;
    @Autowired
    private ProfitLossService profitLossService;
    @Autowired
    private BalanceSheetService balanceSheetService;
    @Autowired
    private AccountingHealthCheckService accountingHealthCheckService;
    @Autowired
    private JournalHeaderRepository journalHeaderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("P4").lastName("Tester").mobile("9000000201").status(CustomerStatus.ACTIVE).build());
    }

    private Supplier newSupplier() {
        return supplierRepository.save(Supplier.builder()
                .name("P4 Supplier").mobile("9000000202").status(SupplierStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = productRepository.save(Product.builder()
                .name("P4 Item " + System.nanoTime())
                .sellingPrice(price).purchasePrice(price).status(ProductStatus.ACTIVE).build());
        inventoryRepository.save(Inventory.builder().product(product).currentStock(stock).build());
        return product;
    }

    private SaleItemRequest saleItem(Long productId, int qty, BigDecimal rate, BigDecimal gstPercent) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setSellingPrice(rate);
        item.setDiscount(BigDecimal.ZERO);
        item.setTax(BigDecimal.ZERO);
        item.setGstPercent(gstPercent);
        return item;
    }

    private PurchaseItemRequest purchaseItem(Long productId, int qty, BigDecimal rate, BigDecimal gstPercent) {
        PurchaseItemRequest item = new PurchaseItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setPurchasePrice(rate);
        item.setDiscount(BigDecimal.ZERO);
        item.setTax(BigDecimal.ZERO);
        item.setGstPercent(gstPercent);
        return item;
    }

    private SaleCreateRequest saleRequest(TransactionType type, Long customerId, BigDecimal paid, List<SaleItemRequest> items, boolean draft) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(paid);
        request.setItems(items);
        request.setTransactionType(type);
        request.setSaveAsDraft(draft);
        return request;
    }

    private PurchaseCreateRequest purchaseRequest(TransactionType type, Long supplierId, BigDecimal paid, List<PurchaseItemRequest> items, boolean draft) {
        PurchaseCreateRequest request = new PurchaseCreateRequest();
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(paid);
        request.setItems(items);
        request.setTransactionType(type);
        request.setSaveAsDraft(draft);
        return request;
    }

    // ---- Test 41: Day Book includes GST Sale, GST Purchase, Receipt, Payment, Kacchi Sale, Kacchi Purchase ----
    @Test
    void dayBook_includesEveryPostedVoucherType_kacchiIncluded() {
        Customer customer = newCustomer();
        Supplier supplier = newSupplier();
        Product saleProduct = newProduct(new BigDecimal("10000"), 20);
        Product purchaseProduct = newProduct(new BigDecimal("8000"), 0);

        SaleResponse gstSale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(saleProduct.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));
        purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(), BigDecimal.ZERO,
                List.of(purchaseItem(purchaseProduct.getId(), 1, new BigDecimal("8000"), new BigDecimal("18"))), false));
        SaleResponse kacchiSale = saleService.createSale(saleRequest(TransactionType.SALE_CHALLAN, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(saleProduct.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));
        purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE_CHALLAN, supplier.getId(), BigDecimal.ZERO,
                List.of(purchaseItem(purchaseProduct.getId(), 1, new BigDecimal("8000"), new BigDecimal("18"))), false));

        ReceiptRequest receiptRequest = new ReceiptRequest();
        receiptRequest.setCustomerId(customer.getId());
        receiptRequest.setReceiptDate(LocalDate.now());
        receiptRequest.setAmount(new BigDecimal("5000"));
        receiptRequest.setPaymentMode(PaymentMode.CASH);
        receiptRequest.setAllocations(new ArrayList<>());
        receiptService.create(receiptRequest);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setSupplierId(supplier.getId());
        paymentRequest.setPaymentDate(LocalDate.now());
        paymentRequest.setAmount(new BigDecimal("3000"));
        paymentRequest.setPaymentMode(PaymentMode.CASH);
        paymentRequest.setAllocations(new ArrayList<>());
        paymentService.create(paymentRequest);

        DayBookResponse dayBook = accountingReportService.dayBook(LocalDate.now(), LocalDate.now());
        List<VoucherType> types = dayBook.getRows().stream().map(r -> r.getVoucherType()).distinct().toList();
        assertThat(types).contains(VoucherType.SALE, VoucherType.PURCHASE, VoucherType.RECEIPT, VoucherType.PAYMENT);

        // Kacchi Sale MUST appear in accounting Day Book (it's a real posted journal), unlike GST reports which exclude it.
        assertThat(dayBook.getRows()).anyMatch(r -> r.getVoucherType() == VoucherType.SALE
                && r.getVoucherNumber() != null && r.getVoucherNumber().equals(kacchiSale.getInvoiceNumber()));

        for (var row : dayBook.getRows()) {
            assertThat(row.getDebit()).isEqualByComparingTo(row.getCredit());
        }
    }

    // ---- Test 48: Kacchi accounting — accounting reports INCLUDE it even though gstReportingApplicable = NO ----
    @Test
    void kacchiSale_includedInAccountingReports_despiteGstReportingApplicableFalse() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse kacchi = saleService.createSale(saleRequest(TransactionType.SALE_CHALLAN, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));

        assertThat(kacchi.isGstReportingApplicable()).isFalse();
        assertThat(kacchi.getTotalTax()).isEqualByComparingTo("1800.00");

        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.SALE, kacchi.getId(), com.storehub.entity.JournalStatus.POSTED)).isTrue();

        DayBookResponse dayBook = accountingReportService.dayBook(LocalDate.now(), LocalDate.now());
        assertThat(dayBook.getRows()).anyMatch(r -> kacchi.getInvoiceNumber().equals(r.getVoucherNumber()));
    }

    // ---- Test 50: Draft transaction never affects accounting reports ----
    @Test
    void draftSale_neverAffectsAccountingReports() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse draft = saleService.createSale(saleRequest(TransactionType.SALE_CHALLAN, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), true));

        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.SALE, draft.getId(), com.storehub.entity.JournalStatus.POSTED)).isFalse();

        DayBookResponse dayBook = accountingReportService.dayBook(LocalDate.now(), LocalDate.now());
        assertThat(dayBook.getRows()).noneMatch(r -> draft.getInvoiceNumber().equals(r.getVoucherNumber()));
    }

    // ---- Test 49: Cancelled sale — original + reversal journal both exist, net effect zero ----
    @Test
    void cancelledSale_originalAndReversalBothExist_netZero() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));

        saleService.cancelSale(sale.getId());

        List<com.storehub.entity.JournalHeader> journals = journalHeaderRepository.findAll().stream()
                .filter(j -> j.getVoucherType() == VoucherType.SALE && sale.getId().equals(j.getVoucherId()))
                .toList();
        assertThat(journals).hasSize(2);
        assertThat(journals).anyMatch(j -> j.getStatus() == com.storehub.entity.JournalStatus.REVERSED);
        assertThat(journals).anyMatch(j -> j.getStatus() == com.storehub.entity.JournalStatus.POSTED && j.getReversalOfJournal() != null);
    }

    // ---- Test 42: Cash Book — closing = opening + receipts - payments, agrees with Cash Account Ledger ----
    @Test
    void cashBook_closingBalance_matchesFormulaAndAccountLedger() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("5000"), 10);

        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), new BigDecimal("5900"),
                List.of(saleItem(product.getId(), 1, new BigDecimal("5000"), new BigDecimal("18"))), false));

        CashBankBookResponse cashBook = cashBankBookService.cashBook(LocalDate.now(), LocalDate.now());
        BigDecimal expectedClosing = cashBook.getOpeningBalance().add(cashBook.getTotalReceipts()).subtract(cashBook.getTotalPayments());
        assertThat(cashBook.getClosingBalance()).isEqualByComparingTo(expectedClosing);

        var ledger = accountingReportService.accountLedger(cashBook.getAccountId(), LocalDate.now(), LocalDate.now());
        BigDecimal ledgerClosingSigned = ledger.getClosingBalanceType() == com.storehub.entity.LedgerEntryType.DEBIT
                ? ledger.getClosingBalance() : ledger.getClosingBalance().negate();
        assertThat(cashBook.getClosingBalance()).isEqualByComparingTo(ledgerClosingSigned);
    }

    // ---- Test 43: Receivable — opening + sales - receipts = outstanding ----
    @Test
    void receivable_outstandingMatchesFormula() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("20000"), 10);

        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("20000"), new BigDecimal("18"))), false));

        ReceiptRequest receiptRequest = new ReceiptRequest();
        receiptRequest.setCustomerId(customer.getId());
        receiptRequest.setReceiptDate(LocalDate.now());
        receiptRequest.setAmount(new BigDecimal("15000"));
        receiptRequest.setPaymentMode(PaymentMode.CASH);
        receiptRequest.setAllocations(new ArrayList<>());
        receiptService.create(receiptRequest);

        ReceivablePayableResponse receivable = receivablePayableService.receivable(LocalDate.now(), LocalDate.now());
        var row = receivable.getRows().stream().filter(r -> r.getPartyId().equals(customer.getId())).findFirst().orElseThrow();

        assertThat(row.getOpeningBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getTransactionAmount()).isEqualByComparingTo(sale.getTotalAmount());
        assertThat(row.getPaymentAmount()).isEqualByComparingTo("15000.00");
        BigDecimal expected = row.getOpeningBalance().add(row.getTransactionAmount()).subtract(row.getPaymentAmount());
        assertThat(row.getClosingOutstanding()).isEqualByComparingTo(expected);
    }

    // ---- Test 44: Payable — opening + purchases - payments = payable ----
    @Test
    void payable_outstandingMatchesFormula() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("50000"), 0);

        var purchase = purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(), BigDecimal.ZERO,
                List.of(purchaseItem(product.getId(), 1, new BigDecimal("50000"), new BigDecimal("18"))), false));

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setSupplierId(supplier.getId());
        paymentRequest.setPaymentDate(LocalDate.now());
        paymentRequest.setAmount(new BigDecimal("30000"));
        paymentRequest.setPaymentMode(PaymentMode.CASH);
        paymentRequest.setAllocations(new ArrayList<>());
        paymentService.create(paymentRequest);

        ReceivablePayableResponse payable = receivablePayableService.payable(LocalDate.now(), LocalDate.now());
        var row = payable.getRows().stream().filter(r -> r.getPartyId().equals(supplier.getId())).findFirst().orElseThrow();

        assertThat(row.getTransactionAmount()).isEqualByComparingTo(purchase.getTotalAmount());
        assertThat(row.getPaymentAmount()).isEqualByComparingTo("30000.00");
        BigDecimal expected = row.getOpeningBalance().add(row.getTransactionAmount()).subtract(row.getPaymentAmount());
        assertThat(row.getClosingOutstanding()).isEqualByComparingTo(expected);
    }

    // ---- Regression: a cancelled Sale's reversal must net to zero transactionAmount, not double-count ----
    @Test
    void receivable_cancelledSale_doesNotInflateTransactionAmount() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("11800"), 10);

        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));
        saleService.cancelSale(sale.getId());

        ReceivablePayableResponse receivable = receivablePayableService.receivable(null, null);
        var row = receivable.getRows().stream().filter(r -> r.getPartyId().equals(customer.getId())).findFirst().orElseThrow();

        assertThat(row.getTransactionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getClosingOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- Test 14: Outstanding Bill report reconciles with Sale.dueAmount ----
    @Test
    void outstandingBillReport_matchesSaleDueAmount() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("12000"), 10);

        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), new BigDecimal("2000"),
                List.of(saleItem(product.getId(), 1, new BigDecimal("12000"), new BigDecimal("18"))), false));

        OutstandingBillReportResponse report = outstandingBillService.customerOutstanding(LocalDate.now());
        var row = report.getRows().stream().filter(r -> sale.getInvoiceNumber().equals(r.getInvoiceNumber())).findFirst().orElseThrow();

        assertThat(row.getOutstanding()).isEqualByComparingTo(sale.getTotalAmount().subtract(new BigDecimal("2000")));
        assertThat(row.getAgeingBucket()).isEqualTo("0-30 Days");
        assertThat(report.getAgeingBasis()).isEqualTo("INVOICE_DATE");
    }

    // ---- Test 45: Trial Balance — total debit = total credit, difference = 0 ----
    @Test
    void trialBalance_alwaysBalances() {
        Customer customer = newCustomer();
        Supplier supplier = newSupplier();
        Product saleProduct = newProduct(new BigDecimal("7000"), 10);
        Product purchaseProduct = newProduct(new BigDecimal("9000"), 0);

        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(saleProduct.getId(), 1, new BigDecimal("7000"), new BigDecimal("18"))), false));
        purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(), BigDecimal.ZERO,
                List.of(purchaseItem(purchaseProduct.getId(), 1, new BigDecimal("9000"), new BigDecimal("18"))), false));

        TrialBalanceResponse trialBalance = accountingReportService.trialBalance(LocalDate.now());
        assertThat(trialBalance.getTotalDebit()).isEqualByComparingTo(trialBalance.getTotalCredit());
        assertThat(trialBalance.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(trialBalance.isBalanced()).isTrue();
    }

    // ---- Test 46: Profit & Loss — derived from Account classifications, not Sale/Purchase tables ----
    @Test
    void profitAndLoss_derivedFromAccountClassifications() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), false));

        ProfitLossResponse pnl = profitLossService.profitAndLoss(LocalDate.now(), LocalDate.now());
        assertThat(pnl.getTotalIncome()).isGreaterThanOrEqualTo(new BigDecimal("10000"));
        assertThat(pnl.getIncomeLines()).anyMatch(l -> "SALES".equals(l.getAccountCode()));
        assertThat(pnl.getNetProfitOrLoss()).isEqualByComparingTo(pnl.getTotalIncome().subtract(pnl.getTotalExpense()));
    }

    // ---- Test 47: Balance Sheet — Assets = Liabilities + Equity, difference = 0 ----
    @Test
    void balanceSheet_assetsEqualLiabilitiesPlusEquity() {
        Customer customer = newCustomer();
        Supplier supplier = newSupplier();
        Product saleProduct = newProduct(new BigDecimal("15000"), 10);
        Product purchaseProduct = newProduct(new BigDecimal("6000"), 0);

        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), new BigDecimal("15000").add(new BigDecimal("2700")),
                List.of(saleItem(saleProduct.getId(), 1, new BigDecimal("15000"), new BigDecimal("18"))), false));
        purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(), BigDecimal.ZERO,
                List.of(purchaseItem(purchaseProduct.getId(), 1, new BigDecimal("6000"), new BigDecimal("18"))), false));

        BalanceSheetResponse balanceSheet = balanceSheetService.balanceSheet(LocalDate.now());
        assertThat(balanceSheet.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balanceSheet.isBalanced()).isTrue();
        assertThat(balanceSheet.getTotalAssets()).isEqualByComparingTo(balanceSheet.getTotalLiabilities().add(balanceSheet.getTotalEquity()));
    }

    // ---- Test 55: Accounting Health Check — Journal Balance and Duplicate Posting must always PASS ----
    @Test
    void healthCheck_journalBalanceAndDuplicatePostingAlwaysPass() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("4000"), 10);
        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("4000"), new BigDecimal("18"))), false));

        AccountingHealthCheckResponse healthCheck = accountingHealthCheckService.runHealthCheck();

        var journalBalance = healthCheck.getFindings().stream().filter(f -> "Journal Balance".equals(f.getCheckName())).findFirst().orElseThrow();
        assertThat(journalBalance.getStatus()).isEqualTo(HealthCheckStatus.PASS);

        var duplicatePosting = healthCheck.getFindings().stream().filter(f -> "Duplicate Posting".equals(f.getCheckName())).findFirst().orElseThrow();
        assertThat(duplicatePosting.getStatus()).isEqualTo(HealthCheckStatus.PASS);
    }

    // ---- Test: Party Ledger reconciles running balance to the closing figure ----
    @Test
    void partyLedger_runningBalanceReachesClosingBalance() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("8000"), 10);

        saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), BigDecimal.ZERO,
                List.of(saleItem(product.getId(), 1, new BigDecimal("8000"), new BigDecimal("18"))), false));

        var ledger = partyLedgerService.partyLedger(AccountingPartyType.CUSTOMER, customer.getId(), LocalDate.now(), LocalDate.now());
        assertThat(ledger.getRows()).isNotEmpty();
        var lastRow = ledger.getRows().get(ledger.getRows().size() - 1);
        assertThat(lastRow.getBalance()).isEqualByComparingTo(ledger.getClosingBalance());
        assertThat(lastRow.getBalanceType()).isEqualTo(ledger.getClosingBalanceType());
    }

    @Autowired
    private PartyLedgerService partyLedgerService;
}
