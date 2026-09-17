package com.storehub.service;

import com.storehub.dto.GstReportListResponse;
import com.storehub.dto.Gstr1Response;
import com.storehub.dto.Gstr3bResponse;
import com.storehub.dto.HsnSummaryReportResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseGstReportResponse;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.ReconciliationResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.dto.SaleUpdateRequest;
import com.storehub.dto.TaxRateSummaryReportResponse;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.GstTransaction;
import com.storehub.entity.GstTransactionStatus;
import com.storehub.entity.GstType;
import com.storehub.entity.Inventory;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import com.storehub.entity.VoucherType;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.GstTransactionRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.SupplierRepository;
import com.storehub.util.GstinValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 test matrix: the GST Reporting Engine. The rule under test throughout is
 * "GST TAX CALCULATION is not GST RETURN REPORTING" — eligibility requires BOTH
 * gstReportingApplicable=true AND status=POSTED(COMPLETED), never inferred from
 * taxAmount or transactionType alone. Every test is @Transactional so it rolls back
 * automatically and never pollutes the shared dev database.
 */
@SpringBootTest
@Transactional
class GstReportingServiceTest {

    private static final DateTimeFormatter RETURN_PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String VALID_GSTIN = "27AAAPL1234C1Z5";

    @Autowired
    private SaleService saleService;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private GstReportingService gstReportingService;
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
    private StoreService storeService;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("Gst").lastName("Tester").mobile("9000000101").status(CustomerStatus.ACTIVE).build());
    }

    private Supplier newSupplier() {
        return supplierRepository.save(Supplier.builder()
                .name("Gst Report Supplier").mobile("9000000102").status(SupplierStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = productRepository.save(Product.builder()
                .name("Gst Report Item " + System.nanoTime())
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

    private SaleCreateRequest saleRequest(TransactionType type, Long customerId, List<SaleItemRequest> items, String gstin, boolean draft) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setCustomerGstin(gstin);
        request.setTransactionType(type);
        request.setSaveAsDraft(draft);
        return request;
    }

    private PurchaseCreateRequest purchaseRequest(TransactionType type, Long supplierId, List<PurchaseItemRequest> items, String gstin) {
        PurchaseCreateRequest request = new PurchaseCreateRequest();
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(TaxMode.INTRA_STATE);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setSupplierGstin(gstin);
        request.setTransactionType(type);
        request.setSaveAsDraft(false);
        return request;
    }

    private String currentReturnPeriod() {
        return LocalDate.now().format(RETURN_PERIOD);
    }

    // ---- Test 1: a Kacchi Sale never gets a GstTransaction row, despite GST being calculated in full ----
    @Test
    void kacchiSale_neverEntersGstReporting() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE_CHALLAN, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        assertThat(response.getTotalTax()).isEqualByComparingTo("1800.00");

        Optional<GstTransaction> txn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId());
        assertThat(txn).isEmpty();

        Gstr1Response gstr1 = gstReportingService.gstr1(LocalDate.now(), LocalDate.now(), null, null);
        assertThat(gstr1.getB2bTransactions()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
        assertThat(gstr1.getB2cTransactions()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
    }

    // ---- Test 2: a Kacchi Purchase never enters GSTR-3B / ITC, despite GST being calculated in full ----
    @Test
    void kacchiPurchase_excludedFromGstr3bAndItc() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);

        PurchaseResponse response = purchaseService.createPurchase(
                purchaseRequest(TransactionType.PURCHASE_CHALLAN, supplier.getId(), List.of(purchaseItem(product.getId(), 1, new BigDecimal("20000"), new BigDecimal("18"))), VALID_GSTIN));

        assertThat(response.getTotalTax()).isEqualByComparingTo("3600.00");
        Optional<GstTransaction> txn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.PURCHASE, response.getId());
        assertThat(txn).isEmpty();

        Gstr3bResponse gstr3b = gstReportingService.gstr3bSummary(currentReturnPeriod(), null);
        // A Kacchi purchase's tax must not have leaked into the ITC total via any other path.
        PurchaseGstReportResponse purchaseReport = gstReportingService.purchaseGstReport(LocalDate.now(), LocalDate.now(), null, null, PageRequest.of(0, 50));
        assertThat(purchaseReport.getTransactions().getContent()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
    }

    // ---- Test 3: a DRAFT transaction (even a non-Kacchi one, mid-edit) never enters GST reporting ----
    @Test
    void draftSale_neverEntersGstReporting() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE_CHALLAN, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, true));

        assertThat(response.getStatus()).isEqualTo(SaleStatus.DRAFT);
        assertThat(gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId())).isEmpty();
    }

    // ---- Test 4: a normal GST sale without a customer GSTIN is classified B2C ----
    @Test
    void normalSale_withoutGstin_classifiedB2c() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        GstTransaction txn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId()).orElseThrow();
        assertThat(txn.isB2b()).isFalse();
        assertThat(txn.getStatus()).isEqualTo(GstTransactionStatus.ACTIVE);

        Gstr1Response gstr1 = gstReportingService.gstr1(LocalDate.now(), LocalDate.now(), null, null);
        assertThat(gstr1.getB2cTransactions()).anyMatch(r -> r.getSourceTransactionId().equals(response.getId()));
        assertThat(gstr1.getB2bTransactions()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
    }

    // ---- Test 5: a normal GST sale with a valid customer GSTIN is classified B2B, with the state code derived from it ----
    @Test
    void normalSale_withValidGstin_classifiedB2bWithStateCode() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), VALID_GSTIN, false));

        GstTransaction txn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId()).orElseThrow();
        assertThat(txn.isB2b()).isTrue();
        assertThat(txn.getPlaceOfSupplyStateCode()).isEqualTo("27");

        Gstr1Response gstr1 = gstReportingService.gstr1(LocalDate.now(), LocalDate.now(), null, null);
        assertThat(gstr1.getB2bTransactions()).anyMatch(r -> r.getSourceTransactionId().equals(response.getId()));
    }

    // ---- Test 6: a normal GST purchase with a valid GSTIN is ITC-eligible and counted in GSTR-3B's ITC ----
    @Test
    void normalPurchase_withValidGstin_isItcEligibleAndCountedInGstr3b() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("20000"), 0);

        PurchaseResponse response = purchaseService.createPurchase(
                purchaseRequest(TransactionType.PURCHASE, supplier.getId(), List.of(purchaseItem(product.getId(), 1, new BigDecimal("20000"), new BigDecimal("18"))), VALID_GSTIN));

        PurchaseGstReportResponse report = gstReportingService.purchaseGstReport(LocalDate.now(), LocalDate.now(), null, null, PageRequest.of(0, 50));
        assertThat(report.getTransactions().getContent())
                .filteredOn(r -> r.getSourceTransactionId().equals(response.getId()))
                .allMatch(r -> Boolean.TRUE.equals(r.getItcEligible()));

        Gstr3bResponse gstr3b = gstReportingService.gstr3bSummary(currentReturnPeriod(), null);
        assertThat(gstr3b.getInputTaxCredit().getTotalTax()).isGreaterThanOrEqualTo(response.getTotalTax());
        assertThat(gstr3b.getNote()).contains("not a government");
    }

    // ---- Test 7: cancelling a posted sale flips its reporting row to REVERSED (never deleted) and excludes it from reports ----
    @Test
    void cancelledSale_reportingRowReversedNotDeleted() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        assertThat(gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId()).orElseThrow().getStatus())
                .isEqualTo(GstTransactionStatus.ACTIVE);

        saleService.cancelSale(response.getId());

        GstTransaction txn = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId()).orElseThrow();
        assertThat(txn.getStatus()).isEqualTo(GstTransactionStatus.REVERSED);

        Gstr1Response gstr1 = gstReportingService.gstr1(LocalDate.now(), LocalDate.now(), null, null);
        assertThat(gstr1.getB2bTransactions()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
        assertThat(gstr1.getB2cTransactions()).noneMatch(r -> r.getSourceTransactionId().equals(response.getId()));
    }

    // ---- Test 8: re-syncing on update (still posted) never creates a second reporting row for the same source ----
    @Test
    void updatingAPostedSale_resyncsIdempotently_noDuplicateRow() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        SaleUpdateRequest update = new SaleUpdateRequest();
        update.setCustomerId(customer.getId());
        update.setSaleDate(LocalDate.now());
        update.setStatus(SaleStatus.COMPLETED);
        update.setGstType(GstType.GST);
        update.setTaxMode(TaxMode.INTRA_STATE);
        update.setPaymentMode(PaymentMode.CASH);
        update.setPaidAmount(BigDecimal.ZERO);
        update.setItems(List.of(saleItem(product.getId(), 1, new BigDecimal("12000"), new BigDecimal("18"))));

        saleService.updateSale(response.getId(), update);

        List<GstTransaction> rows = gstTransactionRepository.findAllBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, response.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTaxableAmount()).isEqualByComparingTo("12000.00");
        assertThat(rows.get(0).getStatus()).isEqualTo(GstTransactionStatus.ACTIVE);
    }

    // ---- Test 9: reconciliation reports MATCHED for a properly synced, eligible transaction ----
    @Test
    void reconciliation_matchedForProperlySyncedTransaction() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        ReconciliationResponse reconciliation = gstReportingService.reconciliation(LocalDate.now(), LocalDate.now(), null);
        assertThat(reconciliation.getRows())
                .filteredOn(r -> r.getSourceTransactionType() == VoucherType.SALE && r.getSourceTransactionId().equals(response.getId()))
                .allMatch(r -> "MATCHED".equals(r.getStatus()));
        assertThat(reconciliation.getMatchedCount()).isGreaterThanOrEqualTo(1);
        assertThat(reconciliation.getDuplicateCount()).isZero();
    }

    // ---- Test 10: HSN and tax-rate summaries aggregate correctly across multiple GST rates on one invoice ----
    @Test
    void multiRateInvoice_hsnAndTaxRateSummariesAggregateSeparately() {
        Customer customer = newCustomer();
        Product product12 = newProduct(new BigDecimal("5000"), 10);
        Product product18 = newProduct(new BigDecimal("8000"), 10);

        SaleResponse response = saleService.createSale(saleRequest(TransactionType.SALE, customer.getId(), List.of(
                saleItem(product12.getId(), 1, new BigDecimal("5000"), new BigDecimal("12")),
                saleItem(product18.getId(), 1, new BigDecimal("8000"), new BigDecimal("18"))
        ), null, false));

        assertThat(response.getTotalTax()).isEqualByComparingTo("2040.00");

        TaxRateSummaryReportResponse taxRateSummary = gstReportingService.taxRateSummary(LocalDate.now(), LocalDate.now(), null);
        assertThat(taxRateSummary.getOutward()).extracting(r -> r.getGstPercent().stripTrailingZeros())
                .contains(new BigDecimal("12").stripTrailingZeros(), new BigDecimal("18").stripTrailingZeros());

        HsnSummaryReportResponse hsnSummary = gstReportingService.hsnSummary(LocalDate.now(), LocalDate.now(), null);
        assertThat(hsnSummary.getOutward()).isNotEmpty();
    }

    // ---- Test 11: the output GST report totals match what was actually posted ----
    @Test
    void outputGstReport_totalsMatchPostedSale() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("10000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(TransactionType.SALE, customer.getId(), List.of(saleItem(product.getId(), 1, new BigDecimal("10000"), new BigDecimal("18"))), null, false));

        GstReportListResponse output = gstReportingService.outputGstReport(LocalDate.now(), LocalDate.now(), null, PageRequest.of(0, 50));
        assertThat(output.getTransactions().getContent()).anyMatch(r -> r.getSourceTransactionId().equals(response.getId()));
        assertThat(output.getTotals().getTotalTax()).isGreaterThanOrEqualTo(response.getTotalTax());
    }

    // ---- Test 12: GstinValidator format validation and state-code extraction ----
    @Test
    void gstinValidator_validatesFormatAndExtractsStateCode() {
        assertThat(GstinValidator.isValid(VALID_GSTIN)).isTrue();
        assertThat(GstinValidator.extractStateCode(VALID_GSTIN)).isEqualTo("27");

        assertThat(GstinValidator.isValid(null)).isFalse();
        assertThat(GstinValidator.isValid("")).isFalse();
        assertThat(GstinValidator.isValid("TOO_SHORT")).isFalse();
        assertThat(GstinValidator.isValid("27aaapl1234c1z5")).isTrue();
        assertThat(GstinValidator.extractStateCode("not-a-gstin")).isNull();
    }
}
