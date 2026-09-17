package com.storehub.service;

import com.storehub.dto.CreditNoteCreateRequest;
import com.storehub.dto.CreditNoteItemRequest;
import com.storehub.dto.CreditNoteResponse;
import com.storehub.dto.DebitNoteCreateRequest;
import com.storehub.dto.DebitNoteItemRequest;
import com.storehub.dto.DebitNoteResponse;
import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.entity.Category;
import com.storehub.entity.CategoryStatus;
import com.storehub.entity.CreditNoteType;
import com.storehub.entity.Customer;
import com.storehub.entity.CustomerStatus;
import com.storehub.entity.DebitNoteType;
import com.storehub.entity.GstType;
import com.storehub.entity.Inventory;
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
import com.storehub.entity.TaxTreatment;
import com.storehub.entity.TransactionType;
import com.storehub.repository.CategoryRepository;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.PurchaseItemRepository;
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

/**
 * End-to-end counterpart to {@link GstCalculationTest}: proves the central
 * GST engine is actually wired correctly through SaleService, PurchaseService,
 * CreditNoteService, DebitNoteService, and ProductService — not just correct
 * in isolation. Covers the remaining GST & Tax Complete spec section 57 cases
 * (3/4 same/inter-state Purchase, 8/9 Credit/Debit Note proportional tax) plus
 * item tax-treatment persistence and Sale-time enforcement.
 */
@SpringBootTest
@Transactional
class GstCalculationIntegrationTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private CreditNoteService creditNoteService;
    @Autowired
    private DebitNoteService debitNoteService;
    @Autowired
    private ProductService productService;
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
    private SaleItemRepository saleItemRepository;
    @Autowired
    private PurchaseItemRepository purchaseItemRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private Customer newCustomer() {
        return customerRepository.save(Customer.builder()
                .firstName("GstCalc").lastName("Tester").mobile("9000000401").status(CustomerStatus.ACTIVE).build());
    }

    private Supplier newSupplier() {
        return supplierRepository.save(Supplier.builder()
                .name("GstCalc Supplier").mobile("9000000402").status(SupplierStatus.ACTIVE).build());
    }

    private Product newProduct(BigDecimal price, int stock) {
        return newProduct(price, stock, TaxTreatment.TAXABLE);
    }

    private Product newProduct(BigDecimal price, int stock, TaxTreatment treatment) {
        Product product = productRepository.save(Product.builder()
                .name("GstCalc Item " + System.nanoTime())
                .sellingPrice(price).purchasePrice(price).status(ProductStatus.ACTIVE)
                .taxTreatment(treatment).build());
        inventoryRepository.save(Inventory.builder().product(product).store(storeService.getOrCreateDefaultStore()).currentStock(stock).build());
        return product;
    }

    private Category ensureCategory() {
        return categoryRepository.findAll().stream().findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("GstCalc Category " + System.nanoTime())
                        .status(CategoryStatus.ACTIVE).build()));
    }

    private SaleItemRequest saleItem(Long productId, int qty, BigDecimal rate, BigDecimal discount, BigDecimal gstPercent) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setSellingPrice(rate);
        item.setDiscount(discount);
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

    private SaleCreateRequest saleRequest(Long customerId, TaxMode taxMode, List<SaleItemRequest> items) {
        SaleCreateRequest request = new SaleCreateRequest();
        request.setCustomerId(customerId);
        request.setSaleDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(taxMode);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setTransactionType(TransactionType.SALE);
        request.setSaveAsDraft(false);
        return request;
    }

    private PurchaseCreateRequest purchaseRequest(Long supplierId, TaxMode taxMode, List<PurchaseItemRequest> items) {
        PurchaseCreateRequest request = new PurchaseCreateRequest();
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setGstType(GstType.GST);
        request.setTaxMode(taxMode);
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setItems(items);
        request.setTransactionType(TransactionType.PURCHASE);
        request.setSaveAsDraft(false);
        return request;
    }

    // ---- Case 1 (end-to-end): same-state GST Sale ----
    @Test
    void sameStateGstSale_viaSaleService_splitsCgstSgst() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(customer.getId(), TaxMode.INTRA_STATE, List.of(saleItem(product.getId(), 1, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("18")))));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1180.00");
    }

    // ---- Case 3: same-state GST Purchase ----
    @Test
    void sameStateGstPurchase_viaPurchaseService_splitsCgstSgst() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("2000"), 0);

        PurchaseResponse response = purchaseService.createPurchase(
                purchaseRequest(supplier.getId(), TaxMode.INTRA_STATE, List.of(purchaseItem(product.getId(), 1, new BigDecimal("2000"), new BigDecimal("18")))));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("180.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("180.00");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2360.00");
    }

    // ---- Case 4: inter-state GST Purchase ----
    @Test
    void interStateGstPurchase_viaPurchaseService_appliesIgst() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("2000"), 0);

        PurchaseResponse response = purchaseService.createPurchase(
                purchaseRequest(supplier.getId(), TaxMode.INTER_STATE, List.of(purchaseItem(product.getId(), 1, new BigDecimal("2000"), new BigDecimal("18")))));

        assertThat(response.getCgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("0");
        assertThat(response.getIgstAmount()).isEqualByComparingTo("360.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2360.00");
    }

    // ---- Case 5 (end-to-end): discount subtracted before GST via SaleService ----
    @Test
    void discountBeforeGst_viaSaleService_taxesOnlyTheNetAmount() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);

        SaleResponse response = saleService.createSale(
                saleRequest(customer.getId(), TaxMode.INTRA_STATE,
                        List.of(saleItem(product.getId(), 1, new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("18")))));

        assertThat(response.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("900.00");
        assertThat(response.getTotalTax()).isEqualByComparingTo("162.00");
        assertThat(response.getCgstAmount()).isEqualByComparingTo("81.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("81.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1062.00");
    }

    // ---- Case 8: Credit Note tax adjustment follows the actual returned value (partial return) ----
    @Test
    void creditNote_taxFollowsReturnedProportion_notTheFullOriginalInvoice() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10);
        SaleResponse sale = saleService.createSale(
                saleRequest(customer.getId(), TaxMode.INTRA_STATE, List.of(saleItem(product.getId(), 5, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("18")))));
        // Full sale: taxable 5000, cgst 450, sgst 450 — sanity-check the source invoice first.
        assertThat(sale.getCgstAmount()).isEqualByComparingTo("450.00");

        SaleItem saleItem = saleItemRepository.findAll().stream()
                .filter(i -> i.getSale().getId().equals(sale.getId())).findFirst().orElseThrow();

        CreditNoteCreateRequest request = new CreditNoteCreateRequest();
        request.setSourceSaleId(sale.getId());
        request.setNoteType(CreditNoteType.SALES_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        CreditNoteItemRequest itemReq = new CreditNoteItemRequest();
        itemReq.setSaleItemId(saleItem.getId());
        itemReq.setQuantity(2); // return 2 of the 5 sold -> 2/5 proportion
        request.setItems(List.of(itemReq));
        request.setPost(true);

        CreditNoteResponse note = creditNoteService.create(request);

        assertThat(note.getTaxableAmount()).isEqualByComparingTo("2000.00");
        assertThat(note.getCgstAmount()).isEqualByComparingTo("180.00");
        assertThat(note.getSgstAmount()).isEqualByComparingTo("180.00");
        assertThat(note.getTotalTax()).isEqualByComparingTo("360.00");
        assertThat(note.getTotalAmount()).isEqualByComparingTo("2360.00");
        assertThat(note.getStatus()).isEqualTo(NoteStatus.POSTED);
    }

    // ---- Case 9: Debit Note tax adjustment follows the actual returned value (partial return) ----
    @Test
    void debitNote_taxFollowsReturnedProportion_notTheFullOriginalInvoice() {
        Supplier supplier = newSupplier();
        Product product = newProduct(new BigDecimal("2000"), 0);
        PurchaseResponse purchase = purchaseService.createPurchase(
                purchaseRequest(supplier.getId(), TaxMode.INTRA_STATE, List.of(purchaseItem(product.getId(), 5, new BigDecimal("2000"), new BigDecimal("18")))));
        // Full purchase: taxable 10000, cgst 900, sgst 900 — sanity-check the source invoice first.
        assertThat(purchase.getCgstAmount()).isEqualByComparingTo("900.00");

        PurchaseItem purchaseItem = purchaseItemRepository.findAll().stream()
                .filter(i -> i.getPurchase().getId().equals(purchase.getId())).findFirst().orElseThrow();

        DebitNoteCreateRequest request = new DebitNoteCreateRequest();
        request.setSourcePurchaseId(purchase.getId());
        request.setNoteType(DebitNoteType.PURCHASE_RETURN);
        request.setNoteDate(LocalDate.now());
        request.setStockImpact(StockImpactType.STOCK_RETURN);
        DebitNoteItemRequest itemReq = new DebitNoteItemRequest();
        itemReq.setPurchaseItemId(purchaseItem.getId());
        itemReq.setQuantity(2); // return 2 of the 5 purchased -> 2/5 proportion
        request.setItems(List.of(itemReq));
        request.setPost(true);

        DebitNoteResponse note = debitNoteService.create(request);

        assertThat(note.getTaxableAmount()).isEqualByComparingTo("4000.00");
        assertThat(note.getCgstAmount()).isEqualByComparingTo("360.00");
        assertThat(note.getSgstAmount()).isEqualByComparingTo("360.00");
        assertThat(note.getTotalTax()).isEqualByComparingTo("720.00");
        assertThat(note.getTotalAmount()).isEqualByComparingTo("4720.00");
        assertThat(note.getStatus()).isEqualTo(NoteStatus.POSTED);
    }

    // ---- Item tax treatment end-to-end: an EXEMPT item computes 0 GST in a Sale even though a rate was requested ----
    @Test
    void exemptProduct_viaSaleService_computesZeroGstDespiteRequestedRate() {
        Customer customer = newCustomer();
        Product product = newProduct(new BigDecimal("1000"), 10, TaxTreatment.EXEMPT);

        SaleResponse response = saleService.createSale(
                saleRequest(customer.getId(), TaxMode.INTRA_STATE, List.of(saleItem(product.getId(), 1, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("18")))));

        assertThat(response.getTotalTax()).isEqualByComparingTo("0");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    // ---- Product create/update round-trips taxTreatment correctly ----
    @Test
    void productService_persistsAndDefaultsTaxTreatment() {
        ProductCreateRequest createReq = new ProductCreateRequest();
        createReq.setName("Taxable Round Trip " + System.nanoTime());
        createReq.setSku("SKU-" + System.nanoTime());
        createReq.setCategoryId(ensureCategory().getId());
        createReq.setPurchasePrice(new BigDecimal("100"));
        createReq.setSellingPrice(new BigDecimal("150"));
        // taxTreatment intentionally left null -> must default to TAXABLE
        ProductResponse created = productService.createProduct(createReq);
        assertThat(created.getTaxTreatment()).isEqualTo(TaxTreatment.TAXABLE);

        ProductUpdateRequest updateReq = new ProductUpdateRequest();
        updateReq.setName(created.getName());
        updateReq.setSku(created.getSku());
        updateReq.setCategoryId(created.getCategoryId());
        updateReq.setPurchasePrice(new BigDecimal("100"));
        updateReq.setSellingPrice(new BigDecimal("150"));
        updateReq.setTaxTreatment(TaxTreatment.ZERO_RATED);
        ProductResponse updated = productService.updateProduct(created.getId(), updateReq);
        assertThat(updated.getTaxTreatment()).isEqualTo(TaxTreatment.ZERO_RATED);
    }
}
