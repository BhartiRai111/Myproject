package com.storehub.service;

import com.storehub.dto.CreditNoteCreateRequest;
import com.storehub.dto.CreditNoteItemRequest;
import com.storehub.dto.CreditNoteResponse;
import com.storehub.dto.DebitNoteCreateRequest;
import com.storehub.dto.DebitNoteItemRequest;
import com.storehub.dto.DebitNoteResponse;
import com.storehub.dto.FinancialYearRequest;
import com.storehub.dto.FinancialYearResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.CreditNoteType;
import com.storehub.entity.DebitNoteType;
import com.storehub.entity.FinancialYearStatus;
import com.storehub.entity.GstTransactionStatus;
import com.storehub.entity.GstType;
import com.storehub.entity.Inventory;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.NoteStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.PurchaseItem;
import com.storehub.entity.SaleItem;
import com.storehub.entity.StockImpactType;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import com.storehub.entity.VoucherType;
import com.storehub.entity.AuditAction;
import com.storehub.entity.AuditLog;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.AuditLogRepository;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.GstTransactionRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.repository.PurchaseItemRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.SaleItemRepository;
import com.storehub.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 test matrix: Financial Year, FY-aware Voucher Numbering, Credit
 * Note, Debit Note. @Transactional rolls back the outer test transaction,
 * but {@link VoucherNumberService#next} runs in its own REQUIRES_NEW
 * transaction (documented there) so voucher sequence counters persist
 * across test runs — this is intentional and does not affect assertions
 * here, which only check relative sequencing within a single test.
 */
@SpringBootTest
@Transactional
class Phase5Test {

    @Autowired
    private FinancialYearService financialYearService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private com.storehub.service.PurchaseService purchaseService;
    @Autowired
    private CreditNoteService creditNoteService;
    @Autowired
    private DebitNoteService debitNoteService;
    @Autowired
    private JournalHeaderRepository journalHeaderRepository;
    @Autowired
    private GstTransactionRepository gstTransactionRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private SaleItemRepository saleItemRepository;
    @Autowired
    private PurchaseItemRepository purchaseItemRepository;
    @Autowired
    private ReceivablePayableService receivablePayableService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("P5").lastName("Tester").mobile("9000000301").status(CustomerStatus.ACTIVE).build());
    }

    private Supplier newSupplier() {
        return supplierRepository.save(Supplier.builder()
                .name("P5 Supplier").mobile("9000000302").status(SupplierStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = productRepository.save(Product.builder()
                .name("P5 Item " + System.nanoTime())
                .sellingPrice(price).purchasePrice(price).status(ProductStatus.ACTIVE).build());
        inventoryRepository.save(Inventory.builder().product(product).currentStock(stock).build());
        return product;
    }

    private SaleItemRequest saleItemReq(Long productId, int qty, BigDecimal rate, BigDecimal gstPercent) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setSellingPrice(rate);
        item.setDiscount(BigDecimal.ZERO);
        item.setTax(BigDecimal.ZERO);
        item.setGstPercent(gstPercent);
        return item;
    }

    private PurchaseItemRequest purchaseItemReq(Long productId, int qty, BigDecimal rate, BigDecimal gstPercent) {
        PurchaseItemRequest item = new PurchaseItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setPurchasePrice(rate);
        item.setDiscount(BigDecimal.ZERO);
        item.setTax(BigDecimal.ZERO);
        item.setGstPercent(gstPercent);
        return item;
    }

    private SaleCreateRequest saleRequest(TransactionType type, Long customerId, List<SaleItemRequest> items) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setTransactionType(type);
        request.setSaveAsDraft(false);
        return request;
    }

    private PurchaseCreateRequest purchaseRequest(TransactionType type, Long supplierId, List<PurchaseItemRequest> items) {
        PurchaseCreateRequest request = new PurchaseCreateRequest();
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setTransactionType(type);
        request.setSaveAsDraft(false);
        return request;
    }

    // ---- Financial Year ----

    @Test
    void currentFinancialYear_seededOnStartup_coversToday() {
        FinancialYearResponse current = financialYearService.getCurrent();
        assertThat(current.getStartDate()).isBeforeOrEqualTo(LocalDate.now());
        assertThat(current.getEndDate()).isAfterOrEqualTo(LocalDate.now());
        assertThat(current.getStatus()).isEqualTo(FinancialYearStatus.OPEN);
    }

    @Test
    void closedFinancialYear_blocksNewPosting() {
        // Exercises AccountingService.postJournal's FinancialYearService.resolveOpenForPosting gate
        // directly (same rule Sale/Purchase/Receipt/Payment/Notes all go through) rather than via a
        // full Sale creation: VoucherNumberService.next() runs in its own REQUIRES_NEW transaction
        // (by design, documented on that class), which cannot see a FinancialYear row this still-open
        // test transaction hasn't committed yet, so routing through createSale() here would fail on
        // that unrelated visibility technicality instead of testing the closed-FY rule itself.
        LocalDate past = LocalDate.of(2019, 6, 15);
        FinancialYearRequest fyReq = new FinancialYearRequest();
        fyReq.setStartDate(LocalDate.of(2019, 4, 1));
        fyReq.setEndDate(LocalDate.of(2020, 3, 31));
        FinancialYearResponse fy = financialYearService.create(fyReq);
        financialYearService.setStatus(fy.getId(), FinancialYearStatus.CLOSED);

        assertThatThrownBy(() -> financialYearService.resolveOpenForPosting(past))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("closed");
    }

    // ---- Voucher Numbering ----

    @Test
    void voucherNumbers_areSequentialAndFYAware() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("500"), 10);

        SaleResponse s1 = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 1, new BigDecimal("500"), BigDecimal.ZERO))));
        SaleResponse s2 = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 1, new BigDecimal("500"), BigDecimal.ZERO))));

        String fyCode = financialYearService.getCurrent().getCode();
        assertThat(s1.getInvoiceNumber()).matches("SALE/" + fyCode.replace("-", "\\-") + "/\\d{6}");
        assertThat(s2.getInvoiceNumber()).isNotEqualTo(s1.getInvoiceNumber());

        int seq1 = Integer.parseInt(s1.getInvoiceNumber().substring(s1.getInvoiceNumber().lastIndexOf('/') + 1));
        int seq2 = Integer.parseInt(s2.getInvoiceNumber().substring(s2.getInvoiceNumber().lastIndexOf('/') + 1));
        assertThat(seq2).isEqualTo(seq1 + 1);
    }

    @Test
    void voucherNumbers_kacchiSaleUsesDistinctSeriesFromRegularSale() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("500"), 10);

        SaleResponse regular = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 1, new BigDecimal("500"), BigDecimal.ZERO))));
        SaleResponse kacchi = saleService.createSale(saleRequest(TransactionType.SALE_CHALLAN, customer.getId(),
                List.of(saleItemReq(product.getId(), 1, new BigDecimal("500"), BigDecimal.ZERO))));

        assertThat(regular.getInvoiceNumber()).startsWith("SALE/");
        assertThat(kacchi.getInvoiceNumber()).startsWith("SC/");
    }

    // ---- Credit Note ----

    @Test
    void creditNote_draft_hasNoSideEffects() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 5, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));
        request.setPost(false);

        CreditNoteResponse note = creditNoteService.create(request);
        assertThat(note.getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CREDIT_NOTE, note.getId(), JournalStatus.POSTED)).isFalse();

        Inventory inv = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inv.getCurrentStock()).isEqualTo(5); // 10 - 5 sold, unaffected by the draft note
    }

    @Test
    void creditNote_post_appliesStockAccountingLedgerGst_thenCancelReversesAll() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 5, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        int stockAfterSale = inventoryRepository.findByProductId(product.getId()).orElseThrow().getCurrentStock();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));
        request.setPost(true);

        CreditNoteResponse note = creditNoteService.create(request);
        assertThat(note.getStatus()).isEqualTo(NoteStatus.POSTED);

        // Accounting: a balanced journal exists
        var journal = journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CREDIT_NOTE, note.getId(), JournalStatus.POSTED).orElseThrow();
        BigDecimal totalDebit = journal.getLines().stream().map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = journal.getLines().stream().map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
        assertThat(totalDebit).isEqualByComparingTo(note.getTotalAmount());

        // Inventory: stock increased by the returned quantity
        Inventory invAfterPost = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfterPost.getCurrentStock()).isEqualTo(stockAfterSale + 2);

        // GST reporting: an ACTIVE row exists for a normal (non-Kacchi) sale's credit note
        var gstTxn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.CREDIT_NOTE, note.getId());
        assertThat(gstTxn).isPresent();
        assertThat(gstTxn.get().getStatus()).isEqualTo(GstTransactionStatus.ACTIVE);

        // Cancel: everything reverses
        CreditNoteResponse cancelled = creditNoteService.cancel(note.getId());
        assertThat(cancelled.getStatus()).isEqualTo(NoteStatus.CANCELLED);

        Inventory invAfterCancel = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfterCancel.getCurrentStock()).isEqualTo(stockAfterSale);

        var gstTxnAfterCancel = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.CREDIT_NOTE, note.getId());
        assertThat(gstTxnAfterCancel.get().getStatus()).isEqualTo(GstTransactionStatus.REVERSED);

        // Idempotent: cancelling again is a no-op, no error, no duplicate reversal
        CreditNoteResponse cancelledAgain = creditNoteService.cancel(note.getId());
        assertThat(cancelledAgain.getStatus()).isEqualTo(NoteStatus.CANCELLED);
        Inventory invAfterSecondCancel = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfterSecondCancel.getCurrentStock()).isEqualTo(stockAfterSale);
    }

    @Test
    void creditNote_cannotReturnMoreThanSoldQuantity() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 3, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(4); // more than the 3 sold
        request.setItems(List.of(itemReq));

        assertThatThrownBy(() -> creditNoteService.create(request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("eligible for return");
    }

    @Test
    void creditNote_cannotReturnAlreadyReturnedQuantityAgain() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 3, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest first = new CreditNoteCreateRequest();
        first.setSourceSaleId(sale.getId());
        first.setNoteType(CreditNoteType.SALES_RETURN);
        first.setNoteDate(LocalDate.now());
        first.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest firstItem = new CreditNoteItemRequest();
        firstItem.setSaleItemId(saleItem.getId());
        firstItem.setQuantity(3);
        first.setItems(List.of(firstItem));
        creditNoteService.create(first); // returns all 3 (as a DRAFT, still reserves the quantity)

        CreditNoteCreateRequest second = new CreditNoteCreateRequest();
        second.setSourceSaleId(sale.getId());
        second.setNoteType(CreditNoteType.SALES_RETURN);
        second.setNoteDate(LocalDate.now());
        second.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest secondItem = new CreditNoteItemRequest();
        secondItem.setSaleItemId(saleItem.getId());
        secondItem.setQuantity(1);
        second.setItems(List.of(secondItem));

        assertThatThrownBy(() -> creditNoteService.create(second)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only 0 remaining");
    }

    @Test
    void creditNote_cannotCreateAgainstCancelledSale() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 1, new BigDecimal("1000"), new BigDecimal("18")))));
        saleService.cancelSale(sale.getId());
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(1);
        request.setItems(List.of(itemReq));

        assertThatThrownBy(() -> creditNoteService.create(request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void creditNote_againstKacchiSale_notGstReportable() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse kacchiSale = saleService.createSale(saleRequest(TransactionType.SALE_CHALLAN, customer.getId(),
                List.of(saleItemReq(product.getId(), 3, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(kacchiSale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(kacchiSale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(1);
        request.setItems(List.of(itemReq));
        request.setPost(true);

        CreditNoteResponse note = creditNoteService.create(request);
        assertThat(note.isGstReportingApplicable()).isFalse();
        assertThat(gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.CREDIT_NOTE, note.getId()))
                .isEmpty();
        // But it still posted an accounting journal and moved stock — Kacchi is never excluded from accounting.
        assertThat(journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.CREDIT_NOTE, note.getId(), JournalStatus.POSTED)).isTrue();
    }

    @Test
    void creditNote_financialAdjustment_doesNotTouchStock() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 3, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();
        int stockAfterSale = inventoryRepository.findByProductId(product.getId()).orElseThrow().getCurrentStock();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.PRICE_ADJUSTMENT);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.FINANCIAL_ADJUSTMENT);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(1);
        request.setItems(List.of(itemReq));
        request.setPost(true);

        creditNoteService.create(request);
        Inventory invAfter = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfter.getCurrentStock()).isEqualTo(stockAfterSale);
    }

    // ---- Debit Note ----

    @Test
    void debitNote_post_decreasesStock_balancedJournal_thenCancelReverses() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("2000"), 0);
        var purchase = purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(),
                List.of(purchaseItemReq(product.getId(), 10, new BigDecimal("2000"), new BigDecimal("18")))));
        PurchaseItem purchaseItem = purchaseItemRepository.findAll().stream()
                .filter(i -> i.getPurchase().getId().equals(purchase.getId())).findFirst().orElseThrow();
        int stockAfterPurchase = inventoryRepository.findByProductId(product.getId()).orElseThrow().getCurrentStock();

        DebitNoteCreateRequest request = new DebitNoteCreateRequest();
        request.setSourcePurchaseId(purchase.getId());
        request.setNoteType(DebitNoteType.PURCHASE_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        DebitNoteItemRequest itemReq = new DebitNoteItemRequest();
        itemReq.setPurchaseItemId(purchaseItem.getId());
        itemReq.setQuantity(3);
        request.setItems(List.of(itemReq));
        request.setPost(true);

        DebitNoteResponse note = debitNoteService.create(request);
        assertThat(note.getStatus()).isEqualTo(NoteStatus.POSTED);

        var journal = journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
                VoucherType.DEBIT_NOTE, note.getId(), JournalStatus.POSTED).orElseThrow();
        BigDecimal totalDebit = journal.getLines().stream().map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = journal.getLines().stream().map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);

        Inventory invAfterPost = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfterPost.getCurrentStock()).isEqualTo(stockAfterPurchase - 3);

        DebitNoteResponse cancelled = debitNoteService.cancel(note.getId());
        assertThat(cancelled.getStatus()).isEqualTo(NoteStatus.CANCELLED);
        Inventory invAfterCancel = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(invAfterCancel.getCurrentStock()).isEqualTo(stockAfterPurchase);
    }

    @Test
    void debitNote_cannotReturnMoreThanPurchasedQuantity() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("2000"), 0);
        var purchase = purchaseService.createPurchase(purchaseRequest(TransactionType.PURCHASE, supplier.getId(),
                List.of(purchaseItemReq(product.getId(), 2, new BigDecimal("2000"), new BigDecimal("18")))));
        PurchaseItem purchaseItem = purchaseItemRepository.findAll().stream()
                .filter(i -> i.getPurchase().getId().equals(purchase.getId())).findFirst().orElseThrow();

        DebitNoteCreateRequest request = new DebitNoteCreateRequest();
        request.setSourcePurchaseId(purchase.getId());
        request.setNoteType(DebitNoteType.PURCHASE_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        DebitNoteItemRequest itemReq = new DebitNoteItemRequest();
        itemReq.setPurchaseItemId(purchaseItem.getId());
        itemReq.setQuantity(5);
        request.setItems(List.of(itemReq));

        assertThatThrownBy(() -> debitNoteService.create(request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("eligible for return");
    }

    @Test
    void receivable_reflectsPostedCreditNote() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 5, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));
        request.setPost(true);
        CreditNoteResponse note = creditNoteService.create(request);

        var receivable = receivablePayableService.receivable(null, null);
        var row = receivable.getRows().stream().filter(r -> r.getPartyId().equals(customer.getId())).findFirst().orElseThrow();
        assertThat(row.getCreditNoteAmount()).isEqualByComparingTo(note.getTotalAmount());
        // closing = transactionAmount(sale total) - creditNoteAmount, since nothing was paid/opened
        assertThat(row.getClosingOutstanding()).isEqualByComparingTo(sale.getTotalAmount().subtract(note.getTotalAmount()));
    }

    // ---- Audit Trail ----

    @Test
    void creditNoteLifecycle_writesCreateAndPostAndCancelAuditEntries() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(),
                List.of(saleItemReq(product.getId(), 3, new BigDecimal("1000"), new BigDecimal("18")))));
        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(1);
        request.setItems(List.of(itemReq));
        request.setPost(true);

        CreditNoteResponse note = creditNoteService.create(request);
        creditNoteService.cancel(note.getId());

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> "CreditNote".equals(a.getEntityType()) && a.getEntityId().equals(note.getId()))
                .toList();
        assertThat(entries).extracting(AuditLog::getAction)
                .contains(AuditAction.CREATE, AuditAction.POST, AuditAction.CANCEL);
        assertThat(entries).allMatch(a -> a.getUsername() != null && a.getTimestamp() != null);
    }

    @Test
    void financialYearStatusChange_writesFyCloseAuditEntry() {
        FinancialYearRequest fyReq = new FinancialYearRequest();
        fyReq.setStartDate(LocalDate.of(2018, 4, 1));
        fyReq.setEndDate(LocalDate.of(2019, 3, 31));
        FinancialYearResponse fy = financialYearService.create(fyReq);
        financialYearService.setStatus(fy.getId(), FinancialYearStatus.CLOSED);

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> "FinancialYear".equals(a.getEntityType()) && a.getEntityId().equals(fy.getId()))
                .toList();
        assertThat(entries).extracting(AuditLog::getAction).contains(AuditAction.CREATE, AuditAction.FY_CLOSE);
    }
}
