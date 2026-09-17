package com.storehub.service;

import com.storehub.dto.CashTransactionCreateRequest;
import com.storehub.dto.CashTransactionResponse;
import com.storehub.dto.DayClosingCloseRequest;
import com.storehub.dto.ExpenseCreateRequest;
import com.storehub.dto.ExpenseResponse;
import com.storehub.dto.ImportResultResponse;
import com.storehub.dto.PaymentMethodRequest;
import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.entity.CashTransactionStatus;
import com.storehub.entity.CashTransactionType;
import com.storehub.entity.Category;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.GstType;
import com.storehub.entity.HealthCheckStatus;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.StockHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6 test matrix: Expense, Cash Transaction, POS idempotency, Payment
 * Method master, reorder detection, Day Closing, Alert Center.
 * @Transactional rolls back automatically, same convention as Phase5Test
 * (whose Javadoc documents the one REQUIRES_NEW exception, voucher counters,
 * which does not affect any assertion here).
 */
@SpringBootTest
@Transactional
class Phase6Test {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private CashTransactionService cashTransactionService;
    @Autowired
    private PaymentMethodService paymentMethodService;
    @Autowired
    private DayClosingService dayClosingService;
    @Autowired
    private AlertService alertService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreService storeService;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private JournalHeaderRepository journalHeaderRepository;
    @Autowired
    private com.storehub.repository.CategoryRepository categoryRepository;
    @Autowired
    private AccountingHealthCheckService accountingHealthCheckService;
    @Autowired
    private StockHistoryRepository stockHistoryRepository;
    @Autowired
    private InventoryService inventoryService;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("P6").lastName("Tester").mobile("9000000401").status(CustomerStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = productRepository.save(Product.builder()
                .name("P6 Item " + System.nanoTime())
                .sellingPrice(price).purchasePrice(price).status(ProductStatus.ACTIVE).build());
        inventoryRepository.save(com.storehub.entity.Inventory.builder().product(product).store(storeService.getOrCreateDefaultStore()).currentStock(stock).build());
        return product;
    }

    private SaleItemRequest saleItemReq(Long productId, int qty, BigDecimal rate) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setSellingPrice(rate);
        item.setDiscount(BigDecimal.ZERO);
        item.setTax(BigDecimal.ZERO);
        item.setGstPercent(BigDecimal.ZERO);
        return item;
    }

    private SaleCreateRequest saleRequest(Long customerId, List<SaleItemRequest> items, String clientRequestId) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setClientRequestId(clientRequestId);
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.NON_GST);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setTransactionType(TransactionType.SALE);
        request.setSaveAsDraft(false);
        return request;
    }

    private BigDecimal totalDebit(Long journalId) {
        return journalHeaderRepository.findById(journalId).orElseThrow().getLines().stream()
                .map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCredit(Long journalId) {
        return journalHeaderRepository.findById(journalId).orElseThrow().getLines().stream()
                .map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Expense ----

    @Test
    void expense_itcEligible_postsInputGstAndBalances_thenCancelReverses() {
        ExpenseCreateRequest request = new ExpenseCreateRequest();
        request.setExpenseDate(LocalDate.now());
        request.setCategory("Internet");
        request.setVendorName("Acme ISP");
        request.setPaymentMode(PaymentMode.BANK);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setGstPercent(new BigDecimal("18"));
        request.setItcEligible(true);
        request.setTaxableAmount(new BigDecimal("1000"));
        request.setPost(true);

        ExpenseResponse expense = expenseService.create(request);
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.POSTED);
        assertThat(expense.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(expense.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(expense.getTotalAmount()).isEqualByComparingTo("1180.00");

        var journal = journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.EXPENSE, expense.getId(), JournalStatus.POSTED).orElseThrow();
        assertThat(totalDebit(journal.getId())).isEqualByComparingTo(totalCredit(journal.getId()));
        assertThat(totalDebit(journal.getId())).isEqualByComparingTo("1180.00");

        ExpenseResponse cancelled = expenseService.cancel(expense.getId());
        assertThat(cancelled.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.EXPENSE, expense.getId(), JournalStatus.POSTED)).isFalse();

        // Idempotent
        ExpenseResponse cancelledAgain = expenseService.cancel(expense.getId());
        assertThat(cancelledAgain.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
    }

    @Test
    void expense_notItcEligible_absorbsTaxIntoExpenseLine_noInputGstAccountDebited() {
        ExpenseCreateRequest request = new ExpenseCreateRequest();
        request.setExpenseDate(LocalDate.now());
        request.setCategory("Rent");
        request.setPaymentMode(PaymentMode.CASH);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setGstPercent(new BigDecimal("18"));
        request.setItcEligible(false);
        request.setTaxableAmount(new BigDecimal("500"));
        request.setPost(true);

        ExpenseResponse expense = expenseService.create(request);
        var journal = journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.EXPENSE, expense.getId(), JournalStatus.POSTED).orElseThrow();
        // Only 2 lines: the whole (taxable+tax) into EXPENSES, and the credit to CASH — no Input GST line.
        assertThat(journal.getLines()).hasSize(2);
        assertThat(totalDebit(journal.getId())).isEqualByComparingTo(totalCredit(journal.getId()));
    }

    @Test
    void expense_requiresTaxModeWhenGstPercentSet() {
        ExpenseCreateRequest request = new ExpenseCreateRequest();
        request.setExpenseDate(LocalDate.now());
        request.setCategory("Transport");
        request.setPaymentMode(PaymentMode.CASH);
        request.setGstPercent(new BigDecimal("5"));
        request.setTaxableAmount(new BigDecimal("200"));

        assertThatThrownBy(() -> expenseService.create(request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tax mode");
    }

    // ---- Cash Transaction ----

    @Test
    void cashTransaction_cashIn_and_cashOut_balance_thenCancelReverses() {
        CashTransactionCreateRequest in = new CashTransactionCreateRequest();
        in.setTransactionDate(LocalDate.now());
        in.setTransactionType(CashTransactionType.CASH_IN);
        in.setPaymentMode(PaymentMode.CASH);
        in.setAmount(new BigDecimal("2000"));
        in.setReason("Owner capital top-up");
        in.setPost(true);

        CashTransactionResponse posted = cashTransactionService.create(in);
        assertThat(posted.getStatus()).isEqualTo(CashTransactionStatus.POSTED);

        var journal = journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CASH_TRANSACTION, posted.getId(), JournalStatus.POSTED).orElseThrow();
        assertThat(totalDebit(journal.getId())).isEqualByComparingTo(totalCredit(journal.getId()));
        assertThat(totalDebit(journal.getId())).isEqualByComparingTo("2000.00");

        CashTransactionResponse cancelled = cashTransactionService.cancel(posted.getId());
        assertThat(cancelled.getStatus()).isEqualTo(CashTransactionStatus.CANCELLED);
        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CASH_TRANSACTION, posted.getId(), JournalStatus.POSTED)).isFalse();
    }

    @Test
    void cashTransaction_draftHasNoJournal() {
        CashTransactionCreateRequest out = new CashTransactionCreateRequest();
        out.setTransactionDate(LocalDate.now());
        out.setTransactionType(CashTransactionType.CASH_OUT);
        out.setPaymentMode(PaymentMode.CASH);
        out.setAmount(new BigDecimal("300"));
        out.setReason("Petty cash");
        out.setPost(false);

        CashTransactionResponse draft = cashTransactionService.create(out);
        assertThat(draft.getStatus()).isEqualTo(CashTransactionStatus.DRAFT);
        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CASH_TRANSACTION, draft.getId(), JournalStatus.POSTED)).isFalse();
    }

    // ---- POS / Sale idempotency ----

    @Test
    void sale_duplicateClientRequestId_returnsSameSale_doesNotCreateSecondRow() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("500"), 10);
        String idempotencyKey = "pos-cart-" + System.nanoTime();

        SaleResponse first = saleService.createSale(
                saleRequest(customer.getId(), List.of(saleItemReq(product.getId(), 2, new BigDecimal("500"))), idempotencyKey));
        SaleResponse second = saleService.createSale(
                saleRequest(customer.getId(), List.of(saleItemReq(product.getId(), 2, new BigDecimal("500"))), idempotencyKey));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(saleRepository.findByClientRequestId(idempotencyKey)).isPresent();

        // Stock should reflect exactly ONE sale of 2 units, not two.
        int stockAfter = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfter).isEqualTo(8);
    }

    @Test
    void sale_withoutClientRequestId_stillWorksNormally() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("100"), 5);
        SaleResponse sale = saleService.createSale(saleRequest(customer.getId(), List.of(saleItemReq(product.getId(), 1, new BigDecimal("100"))), null));
        assertThat(sale.getId()).isNotNull();
    }

    // ---- Payment Method master ----

    @Test
    void paymentMethod_create_rejectsDuplicateName_deactivateKeepsRecord() {
        PaymentMethodRequest request = new PaymentMethodRequest();
        request.setName("UPI - GPay " + System.nanoTime());
        request.setType(PaymentMode.UPI);

        var created = paymentMethodService.create(request);
        assertThat(created.isActive()).isTrue();

        assertThatThrownBy(() -> paymentMethodService.create(request)).isInstanceOf(BadRequestException.class);

        var deactivated = paymentMethodService.setActive(created.getId(), false);
        assertThat(deactivated.isActive()).isFalse();
        assertThat(paymentMethodService.listActive()).noneMatch(p -> p.getId().equals(created.getId()));
        assertThat(paymentMethodService.listAll()).anyMatch(p -> p.getId().equals(created.getId()));
    }

    // ---- Reorder / low stock ----

    @Test
    void product_reorderLevel_flagsAsReorderCandidate() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("Reorder Test Item " + System.nanoTime());
        request.setSku("RT-" + System.nanoTime());
        request.setCategoryId(ensureCategory());
        request.setPurchasePrice(new BigDecimal("50"));
        request.setSellingPrice(new BigDecimal("80"));
        request.setMinStockLevel(2);
        request.setReorderLevel(5);
        request.setReorderQuantity(20);

        var created = productService.createProduct(request);
        assertThat(inventoryRepository.findReorderCandidates(null)).anyMatch(i -> i.getProduct().getId().equals(created.getId()));
    }

    private Long ensureCategory() {
        return categoryRepository.findAll().stream().findFirst()
                .map(c -> c.getId())
                .orElseGet(() -> categoryRepository.save(com.storehub.entity.Category.builder()
                        .name("P6 Category " + System.nanoTime()).build()).getId());
    }

    // ---- Day Closing ----

    @Test
    void dayClosing_matchingCash_closesWithoutReason() {
        LocalDate date = LocalDate.now();
        var summary = dayClosingService.computeSummary(date);

        DayClosingCloseRequest closeRequest = new DayClosingCloseRequest();
        closeRequest.setClosingDate(date);
        closeRequest.setActualCash(summary.getExpectedCash());

        var closed = dayClosingService.close(closeRequest);
        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> dayClosingService.close(closeRequest)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been closed");
    }

    @Test
    void dayClosing_mismatchedCash_requiresReason() {
        LocalDate date = LocalDate.now();
        var summary = dayClosingService.computeSummary(date);

        DayClosingCloseRequest closeRequest = new DayClosingCloseRequest();
        closeRequest.setClosingDate(date);
        closeRequest.setActualCash(summary.getExpectedCash().add(new BigDecimal("50")));

        assertThatThrownBy(() -> dayClosingService.close(closeRequest)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason");

        closeRequest.setDifferenceReason("Till was short-counted by staff");
        var closed = dayClosingService.close(closeRequest);
        assertThat(closed.getDifference()).isEqualByComparingTo("50.00");
    }

    // ---- Alerts ----

    @Test
    void alerts_outOfStockProduct_producesInventoryAlert() {
        newProduct(new BigDecimal("10"), 0);
        var alerts = alertService.getAlerts();
        assertThat(alerts).anyMatch(a -> "Inventory".equals(a.getCategory()) && a.getMessage().contains("OUT OF STOCK"));
    }

    @Test
    void healthCheck_stillPassesJournalBalance_afterPhase6Postings() {
        // A cheap regression signal: Journal Balance / Duplicate Posting must stay PASS after Expense + CashTransaction posting above.
        ExpenseCreateRequest request = new ExpenseCreateRequest();
        request.setExpenseDate(LocalDate.now());
        request.setCategory("Packaging");
        request.setPaymentMode(PaymentMode.CASH);
        request.setTaxableAmount(new BigDecimal("150"));
        request.setPost(true);
        expenseService.create(request);

        var findings = accountingHealthCheckService.runHealthCheck().getFindings();
        assertThat(findings).filteredOn(f -> "Journal Balance".equals(f.getCheckName()))
                .allMatch(f -> f.getStatus() == HealthCheckStatus.PASS);
        assertThat(findings).filteredOn(f -> "Duplicate Posting".equals(f.getCheckName()))
                .allMatch(f -> f.getStatus() == HealthCheckStatus.PASS);
    }

    // ---- CSV export/import ----

    private Category ensureCategoryEntity() {
        Long id = ensureCategory();
        return categoryRepository.findById(id).orElseThrow();
    }

    @Test
    void productExport_csv_includesHeaderAndCreatedProduct() {
        String sku = "EXP-" + System.nanoTime();
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("Export Test Item " + System.nanoTime());
        request.setSku(sku);
        request.setCategoryId(ensureCategory());
        request.setPurchasePrice(new BigDecimal("40"));
        request.setSellingPrice(new BigDecimal("60"));
        var created = productService.createProduct(request);

        String csv = productService.exportCsv(sku, null, null);
        assertThat(csv).startsWith("name,sku,barcode,category");
        assertThat(csv).contains(created.getSku());
        assertThat(csv).contains(created.getName());
    }

    @Test
    void productImport_csv_createsNewProduct_appliesOpeningStockViaInventoryMovement() {
        Category category = ensureCategoryEntity();
        String sku = "IMP-NEW-" + System.nanoTime();
        String csv = "name,sku,category,purchasePrice,sellingPrice,openingStock\n"
                + "Imported Item," + sku + "," + category.getName() + ",25,45,30\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportResultResponse result = productService.importCsv(file);
        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getUpdated()).isZero();
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getErrors()).isEmpty();

        Product product = productRepository.findBySkuIgnoreCase(sku).orElseThrow();
        int stock = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stock).isEqualTo(30);

        var history = stockHistoryRepository.findByProductIdOrderByCreatedAtDesc(product.getId(),
                org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        assertThat(history).anyMatch(h -> h.getMovementType() == StockMovementType.STOCK_IN
                && h.getReferenceType() == ReferenceType.MANUAL
                && "Opening stock via CSV import".equals(h.getReason()));
    }

    @Test
    void productImport_csv_updatesExistingProduct_bySku_withoutTouchingCurrentStock() {
        Category category = ensureCategoryEntity();
        Product existing = newProduct(new BigDecimal("70"), 12);
        existing.setSku("IMP-UPD-" + System.nanoTime());
        productRepository.save(existing);

        String csv = "name,sku,category,purchasePrice,sellingPrice\n"
                + existing.getName() + "," + existing.getSku() + "," + category.getName() + ",70,99\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportResultResponse result = productService.importCsv(file);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getCreated()).isZero();

        Product reloaded = productRepository.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getSellingPrice()).isEqualByComparingTo("99");
        int stock = inventoryRepository.findByProductId(existing.getId()).get(0).getCurrentStock();
        assertThat(stock).isEqualTo(12);
    }

    @Test
    void productImport_csv_unknownCategory_skipsRowWithError() {
        String sku = "IMP-BADCAT-" + System.nanoTime();
        String csv = "name,sku,category,purchasePrice,sellingPrice\n"
                + "Bad Category Item," + sku + ",Nonexistent Category XYZ,10,20\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportResultResponse result = productService.importCsv(file);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getCreated()).isZero();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("category") && e.contains("not found"));
        assertThat(productRepository.findBySkuIgnoreCase(sku)).isEmpty();
    }

    @Test
    void inventoryExport_csv_includesHeaderAndCurrentStock() {
        Product product = newProduct(new BigDecimal("15"), 7);
        product.setSku("INV-EXP-" + System.nanoTime());
        productRepository.save(product);

        String csv = inventoryService.exportCsv(product.getSku(), null, null, null);
        assertThat(csv).startsWith("store,product,sku,category");
        assertThat(csv).contains(product.getSku());
        assertThat(csv).contains(",7,");
    }
}
