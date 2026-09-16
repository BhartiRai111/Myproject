package com.storehub.service;

import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.entity.BarcodeType;
import com.storehub.entity.Category;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.CategoryRepository;
import com.storehub.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StoreHub Barcode Management (Step 2) test matrix: uniqueness, normalization
 * (trim only — unlike SKU, case and leading zeroes are never altered),
 * EAN-13 checksum validation, edit-protection once transactions exist, the
 * authoritative lookup used by POS/Sales/Purchase scanning (active vs
 * inactive vs unknown), and concurrency-safe internal barcode generation.
 * {@code @Transactional} rolls back automatically — the one exception is
 * {@link BarcodeGeneratorService#generateNext()}'s own {@code REQUIRES_NEW}
 * transaction, same caveat as {@code SkuGeneratorService} in SkuManagementTest.
 */
@SpringBootTest
@Transactional
class BarcodeManagementTest {

    /**
     * A unique, checksum-valid EAN-13 computed per test run (rather than a fixed real-world
     * barcode) so this test never collides with a leftover product from a manual/Playwright
     * session using the same fixed value.
     */
    private static final String VALID_EAN13 = generateValidEan13();
    /** Same digits with the check digit flipped — same length/shape, invalid checksum. */
    private static final String INVALID_EAN13_CHECKSUM =
            VALID_EAN13.substring(0, 12) + ((Character.getNumericValue(VALID_EAN13.charAt(12)) + 1) % 10);

    private static String generateValidEan13() {
        String twelveDigits = String.valueOf(System.nanoTime()).replaceAll("[^0-9]", "");
        twelveDigits = (twelveDigits + "000000000000").substring(0, 12);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return twelveDigits + checkDigit;
    }

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private BarcodeGeneratorService barcodeGeneratorService;

    private Long ensureCategory() {
        return categoryRepository.findAll().stream().findFirst()
                .map(Category::getId)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Barcode Test Category " + System.nanoTime()).build()).getId());
    }

    private ProductCreateRequest baseRequest(String barcode) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("Barcode Test Item " + System.nanoTime());
        request.setSku("BC-" + System.nanoTime());
        request.setBarcode(barcode);
        request.setCategoryId(ensureCategory());
        request.setPurchasePrice(new BigDecimal("10"));
        request.setSellingPrice(new BigDecimal("15"));
        return request;
    }

    @Test
    void createProduct_uniqueBarcode_succeeds() {
        String barcode = "890" + System.nanoTime() % 10000000L;
        ProductResponse created = productService.createProduct(baseRequest(barcode));
        assertThat(created.getBarcode()).isEqualTo(barcode);
    }

    @Test
    void createProduct_duplicateBarcode_caseInsensitive_isRejected() {
        String barcode = "DUPBC" + System.nanoTime();
        productService.createProduct(baseRequest(barcode));

        ProductCreateRequest second = baseRequest(barcode.toLowerCase());
        assertThatThrownBy(() -> productService.createProduct(second))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createProduct_barcodeIsTrimmedOnly_caseAndDigitsUnchanged() {
        String rawBarcode = "  MixedCase-123  ";
        ProductResponse created = productService.createProduct(baseRequest(rawBarcode));
        // Trimmed, but NOT uppercased — unlike SKU, a barcode's case is meaningful (Code128 etc.).
        assertThat(created.getBarcode()).isEqualTo("MixedCase-123");
    }

    @Test
    void createProduct_leadingZeroBarcode_preservedExactly() {
        String barcode = "0012345678";
        ProductResponse created = productService.createProduct(baseRequest(barcode));
        assertThat(created.getBarcode()).isEqualTo("0012345678");
        assertThat(created.getBarcodeType()).isEqualTo(BarcodeType.OTHER);

        Product reloaded = productRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getBarcode()).isEqualTo("0012345678");
    }

    @Test
    void createProduct_validEan13_acceptedAndClassified() {
        ProductResponse created = productService.createProduct(baseRequest(VALID_EAN13));
        assertThat(created.getBarcode()).isEqualTo(VALID_EAN13);
        assertThat(created.getBarcodeType()).isEqualTo(BarcodeType.EAN13);
    }

    @Test
    void createProduct_invalidEan13Checksum_isRejected() {
        ProductCreateRequest request = baseRequest(INVALID_EAN13_CHECKSUM);
        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void search_findsProductByBarcode() {
        String barcode = "SEARCHBC" + System.nanoTime();
        productService.createProduct(baseRequest(barcode));

        var page = productService.searchProducts(barcode, null, null, 0, 10, "name", "asc");
        assertThat(page.getContent()).anyMatch(p -> barcode.equals(p.getBarcode()));
    }

    @Test
    void findByBarcode_exactMatch_returnsActiveProduct() {
        String barcode = "LOOKUP" + System.nanoTime();
        ProductResponse created = productService.createProduct(baseRequest(barcode));

        ProductResponse found = productService.findByBarcode(barcode);
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void findByBarcode_inactiveItem_throwsBadRequestNotNotFound() {
        String barcode = "INACTIVEBC" + System.nanoTime();
        ProductResponse created = productService.createProduct(baseRequest(barcode));
        productService.setStatus(created.getId(), ProductStatus.INACTIVE);

        assertThatThrownBy(() -> productService.findByBarcode(barcode))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void findByBarcode_unknownBarcode_throwsNotFound() {
        assertThatThrownBy(() -> productService.findByBarcode("NO-SUCH-BARCODE-" + System.nanoTime()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProduct_barcodeChange_blockedOnceItemHasStockHistory() {
        ProductResponse created = productService.createProduct(baseRequest("HISTBC" + System.nanoTime()));
        inventoryService.applyMovement(created.getId(), 10, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "Test stock-in");

        ProductUpdateRequest update = toUpdateRequest(created, "HISTBC-CHANGED-" + System.nanoTime());
        assertThatThrownBy(() -> productService.updateProduct(created.getId(), update))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("history");

        // Editing other fields (not the barcode) must still be allowed.
        ProductUpdateRequest sameBarcodeUpdate = toUpdateRequest(created, created.getBarcode());
        sameBarcodeUpdate.setSellingPrice(new BigDecimal("77"));
        ProductResponse updated = productService.updateProduct(created.getId(), sameBarcodeUpdate);
        assertThat(updated.getSellingPrice()).isEqualByComparingTo("77");
        assertThat(updated.getBarcode()).isEqualTo(created.getBarcode());
    }

    @Test
    void generateBarcode_producesInternalFormat() {
        String barcode = barcodeGeneratorService.generateNext();
        assertThat(barcode).matches("^INT-\\d{6}$");
    }

    @Test
    void generateBarcode_concurrentCalls_neverDuplicate() throws InterruptedException {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<String> results = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    results.add(barcodeGeneratorService.generateNext());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(threads);
    }

    @Test
    void legacyProductWithNullBarcode_remainsReadable() {
        Product legacy = productRepository.save(Product.builder()
                .name("Legacy No-Barcode Item " + System.nanoTime())
                .sku("LEGACY-" + System.nanoTime())
                .sellingPrice(new BigDecimal("20"))
                .purchasePrice(new BigDecimal("15"))
                .status(ProductStatus.ACTIVE)
                .build());

        ProductResponse fetched = productService.getProductById(legacy.getId());
        assertThat(fetched.getBarcode()).isNull();
        assertThat(fetched.getBarcodeType()).isNull();
    }

    private ProductUpdateRequest toUpdateRequest(ProductResponse product, String barcode) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName(product.getName());
        request.setSku(product.getSku());
        request.setBarcode(barcode);
        request.setCategoryId(product.getCategoryId());
        request.setPurchasePrice(product.getPurchasePrice());
        request.setSellingPrice(product.getSellingPrice());
        return request;
    }
}
