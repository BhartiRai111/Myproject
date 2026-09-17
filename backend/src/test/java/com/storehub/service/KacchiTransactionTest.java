package com.storehub.service;

import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.GstType;
import com.storehub.entity.Inventory;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.CustomerLedgerEntryRepository;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.StockHistoryRepository;
import com.storehub.repository.SupplierLedgerEntryRepository;
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
 * Phase 2 test scenarios: Kacchi Sale / Sale Challan and Kacchi Purchase /
 * Purchase Challan. The core rule under test throughout is "Kacchi does not
 * mean tax-free": GST is calculated in full exactly like a normal GST
 * transaction, only {@code gstReportingApplicable} differs. Every test is
 * @Transactional so it rolls back automatically and never pollutes the
 * shared dev database.
 */
@SpringBootTest
@Transactional
class KacchiTransactionTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreService storeService;
    @Autowired
    private StockHistoryRepository stockHistoryRepository;
    @Autowired
    private CustomerLedgerEntryRepository customerLedgerEntryRepository;
    @Autowired
    private SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    @Autowired
    private JournalHeaderRepository journalHeaderRepository;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("Kacchi").lastName("Tester").mobile("9000000001").status(CustomerStatus.ACTIVE).build());
    }

    private Supplier newSupplier() {
        return supplierRepository.save(Supplier.builder()
                .name("Kacchi Supplier").mobile("9000000002").status(SupplierStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = productRepository.save(Product.builder()
                .name("Kacchi Test Item " + System.nanoTime())
                .sellingPrice(price).purchasePrice(price).status(ProductStatus.ACTIVE).build());
        inventoryRepository.save(Inventory.builder().product(product).store(storeService.getOrCreateDefaultStore()).currentStock(stock).build());
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

    private SaleCreateRequest kacchiSaleRequest(Long customerId, Long productId, TaxMode taxMode, boolean draft) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(taxMode);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(List.of(saleItem(productId, 1, new BigDecimal("10000"), new BigDecimal("18"))));
        request.setTransactionType(TransactionType.SALE_CHALLAN);
        request.setSaveAsDraft(draft);
        return request;
    }

    private PurchaseCreateRequest kacchiPurchaseRequest(Long supplierId, Long productId, TaxMode taxMode, boolean draft) {
        PurchaseCreateRequest request = new PurchaseCreateRequest();
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(taxMode);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(List.of(purchaseItem(productId, 1, new BigDecimal("20000"), new BigDecimal("18"))));
        request.setTransactionType(TransactionType.PURCHASE_CHALLAN);
        request.setSaveAsDraft(draft);
        return request;
    }

    // ---- Test 1: Kacchi Sale, same state ----
    @Test
    void kacchiSaleSameState_calculatesGstAndExcludesReporting() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);
        int stockBefore = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();

        SaleResponse response = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, false));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("900.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("900.00");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getTotalTax()).isEqualByComparingTo("1800.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("11800.00");
        assertThat(response.isGstReportingApplicable()).isFalse();
        assertThat(response.getTransactionType()).isEqualTo(TransactionType.SALE_CHALLAN);

        int stockAfter = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfter).isEqualTo(stockBefore - 1);

        BigDecimal outstanding = customerLedgerEntryRepository.getOutstandingForCustomer(customer.getId());
        assertThat(outstanding).isEqualByComparingTo("11800.00");

        Sale sale = saleRepository.findById(response.getId()).orElseThrow();
        assertThat(GstReportingEligibility.isEligibleForGstReporting(sale)).isFalse();

        JournalHeader journal = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.SALE, response.getId(), com.storehub.entity.JournalStatus.POSTED)
                .orElseThrow();
        assertThat(sumDebit(journal)).isEqualByComparingTo(sumCredit(journal));
        assertThat(sumDebit(journal)).isEqualByComparingTo("11800.00");
    }

    // ---- Test 2: Kacchi Sale, inter-state ----
    @Test
    void kacchiSaleInterState_calculatesIgstAndExcludesReporting() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTER_STATE, false));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("1800.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("11800.00");
        assertThat(response.isGstReportingApplicable()).isFalse();
    }

    // ---- Test 3: Kacchi Purchase, same state ----
    @Test
    void kacchiPurchaseSameState_calculatesGstAndExcludesReporting() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);
        int stockBefore = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();

        PurchaseResponse response = purchaseService.createPurchase(kacchiPurchaseRequest(supplier.getId(), product.getId(), TaxMode.INTRA_STATE, false));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("1800.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("1800.00");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getTotalTax()).isEqualByComparingTo("3600.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("23600.00");
        assertThat(response.isGstReportingApplicable()).isFalse();

        int stockAfter = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfter).isEqualTo(stockBefore + 1);

        BigDecimal outstanding = supplierLedgerEntryRepository.getOutstandingForSupplier(supplier.getId());
        assertThat(outstanding).isEqualByComparingTo("23600.00");

        JournalHeader journal = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.PURCHASE, response.getId(), com.storehub.entity.JournalStatus.POSTED)
                .orElseThrow();
        assertThat(sumDebit(journal)).isEqualByComparingTo(sumCredit(journal));
        assertThat(sumDebit(journal)).isEqualByComparingTo("23600.00");
    }

    // ---- Test 4: Kacchi Purchase, inter-state ----
    @Test
    void kacchiPurchaseInterState_calculatesIgstAndExcludesReporting() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);

        PurchaseResponse response = purchaseService.createPurchase(kacchiPurchaseRequest(supplier.getId(), product.getId(), TaxMode.INTER_STATE, false));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("3600.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("23600.00");
        assertThat(response.isGstReportingApplicable()).isFalse();
    }

    // ---- Test 5: Draft Kacchi Sale has no side effects ----
    @Test
    void draftKacchiSale_hasNoStockLedgerOrAccountingEffects() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);
        int stockBefore = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();

        SaleResponse response = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, true));

        assertThat(response.getStatus()).isEqualTo(SaleStatus.DRAFT);
        // GST is still fully calculated even though nothing is posted yet.
        assertThat(response.getTotalAmount()).isEqualByComparingTo("11800.00");

        int stockAfter = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfter).isEqualTo(stockBefore);

        assertThat(customerLedgerEntryRepository.getOutstandingForCustomer(customer.getId())).isEqualByComparingTo("0");
        assertThat(journalHeaderRepository
                .existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.SALE, response.getId(), com.storehub.entity.JournalStatus.POSTED))
                .isFalse();
    }

    // ---- Test 6: Cancel a posted Kacchi Sale ----
    @Test
    void cancelKacchiSale_reversesStockLedgerAndAccounting() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);
        int stockBefore = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();

        SaleResponse response = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, false));
        int stockAfterSale = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfterSale).isEqualTo(stockBefore - 1);

        saleService.cancelSale(response.getId());

        int stockAfterCancel = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfterCancel).isEqualTo(stockBefore);

        List<JournalHeader> journals = journalHeaderRepository.findAll().stream()
                .filter(j -> j.getVoucherType() == VoucherType.SALE && response.getId().equals(j.getVoucherId()))
                .toList();
        assertThat(journals).hasSize(2);
        assertThat(journals.stream().anyMatch(j -> j.getReversalOfJournal() != null)).isTrue();
        assertThat(journals.stream().filter(j -> j.getReversalOfJournal() == null).findFirst().orElseThrow().getStatus())
                .isEqualTo(com.storehub.entity.JournalStatus.REVERSED);

        assertThat(customerLedgerEntryRepository.getOutstandingForCustomer(customer.getId())).isEqualByComparingTo("0");
    }

    // ---- Test 7: Cancel a posted Kacchi Purchase ----
    @Test
    void cancelKacchiPurchase_reversesStockLedgerAndAccounting() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);
        int stockBefore = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();

        PurchaseResponse response = purchaseService.createPurchase(kacchiPurchaseRequest(supplier.getId(), product.getId(), TaxMode.INTRA_STATE, false));
        int stockAfterPurchase = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfterPurchase).isEqualTo(stockBefore + 1);

        purchaseService.cancelPurchase(response.getId());

        int stockAfterCancel = inventoryRepository.findByProductId(product.getId()).get(0).getCurrentStock();
        assertThat(stockAfterCancel).isEqualTo(stockBefore);

        List<JournalHeader> journals = journalHeaderRepository.findAll().stream()
                .filter(j -> j.getVoucherType() == VoucherType.PURCHASE && response.getId().equals(j.getVoucherId()))
                .toList();
        assertThat(journals).hasSize(2);
        assertThat(journals.stream().anyMatch(j -> j.getReversalOfJournal() != null)).isTrue();

        assertThat(supplierLedgerEntryRepository.getOutstandingForSupplier(supplier.getId())).isEqualByComparingTo("0");
    }

    // ---- Test 8: GST must never be zeroed out for a Kacchi transaction ----
    @Test
    void kacchiSale_gstIsNeverZero() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, false));

        assertThat(response.getTotalTax()).isEqualByComparingTo("1800.00");
        assertThat(response.getTotalTax()).isNotEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.isGstReportingApplicable()).isFalse();
    }

    // ---- Test 9: normal GST Sale is unaffected by the Kacchi changes ----
    @Test
    void normalGstSale_stillReportsForGstReturns() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleCreateRequest request = kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, false);
        request.setTransactionType(TransactionType.SALE);

        SaleResponse response = saleService.createSale(request);

        assertThat(response.getTransactionType()).isEqualTo(TransactionType.SALE);
        assertThat(response.isGstReportingApplicable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(SaleStatus.COMPLETED);
    }

    // ---- Test 10: normal GST Purchase is unaffected by the Kacchi changes ----
    @Test
    void normalGstPurchase_stillReportsForGstReturns() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);

        PurchaseCreateRequest request = kacchiPurchaseRequest(supplier.getId(), product.getId(), TaxMode.INTRA_STATE, false);
        request.setTransactionType(TransactionType.PURCHASE);

        PurchaseResponse response = purchaseService.createPurchase(request);

        assertThat(response.getTransactionType()).isEqualTo(TransactionType.PURCHASE);
        assertThat(response.isGstReportingApplicable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
    }

    // ---- Test 11: accounting stays balanced for a posted Kacchi transaction ----
    @Test
    void kacchiTransactions_accountingStaysBalanced() {
        Customer customer = newCustomer();
        Product saleProduct = newProduct(new BigDecimal("10000"), 10);
        SaleResponse sale = saleService.createSale(kacchiSaleRequest(customer.getId(), saleProduct.getId(), TaxMode.INTRA_STATE, false));

        Supplier supplier = newSupplier();
        Product purchaseProduct = newProduct(new BigDecimal("20000"), 0);
        PurchaseResponse purchase = purchaseService.createPurchase(kacchiPurchaseRequest(supplier.getId(), purchaseProduct.getId(), TaxMode.INTER_STATE, false));

        JournalHeader saleJournal = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.SALE, sale.getId(), com.storehub.entity.JournalStatus.POSTED)
                .orElseThrow();
        JournalHeader purchaseJournal = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.PURCHASE, purchase.getId(), com.storehub.entity.JournalStatus.POSTED)
                .orElseThrow();

        assertThat(sumDebit(saleJournal)).isEqualByComparingTo(sumCredit(saleJournal));
        assertThat(sumDebit(purchaseJournal)).isEqualByComparingTo(sumCredit(purchaseJournal));
    }

    // ---- Test 12: draft posted twice must be rejected the second time ----
    @Test
    void postingADraftChallanTwice_rejectsTheSecondAttempt() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse draft = saleService.createSale(kacchiSaleRequest(customer.getId(), product.getId(), TaxMode.INTRA_STATE, true));
        assertThat(draft.getStatus()).isEqualTo(SaleStatus.DRAFT);

        SaleResponse posted = saleService.postSaleChallan(draft.getId());
        assertThat(posted.getStatus()).isEqualTo(SaleStatus.COMPLETED);

        assertThatThrownBy(() -> saleService.postSaleChallan(draft.getId())).isInstanceOf(BadRequestException.class);

        long journalCount = journalHeaderRepository.findAll().stream()
                .filter(j -> j.getVoucherType() == VoucherType.SALE && draft.getId().equals(j.getVoucherId()))
                .count();
        assertThat(journalCount).isEqualTo(1);

        long stockMovementCount = stockHistoryRepository.findByProductIdOrderByCreatedAtDesc(product.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 50))
                .stream()
                .filter(h -> h.getReferenceId() != null && h.getReferenceId().equals(draft.getId()))
                .count();
        assertThat(stockMovementCount).isEqualTo(1);
    }

    private BigDecimal sumDebit(JournalHeader header) {
        return header.getLines().stream().map(com.storehub.entity.JournalDetail::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCredit(JournalHeader header) {
        return header.getLines().stream().map(com.storehub.entity.JournalDetail::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
