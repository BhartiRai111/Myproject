package com.storehub.service;

import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.entity.Category;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.CategoryRepository;
import com.storehub.repository.ProductRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StoreHub SKU Management (Step 1) test matrix: normalization, uniqueness,
 * validation, edit-protection once transactions exist, deactivation, search,
 * and concurrency-safe auto-generation. {@code @Transactional} rolls back
 * automatically (same convention as Phase5Test/Phase6Test) — the one
 * exception is {@link SkuGeneratorService#generateNext()}, which runs in its
 * own {@code REQUIRES_NEW} transaction and therefore commits its counter
 * increments independently of this test's rollback, exactly like
 * VoucherNumberService in Phase5Test.
 */
@SpringBootTest
@Transactional
class SkuManagementTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private SkuGeneratorService skuGeneratorService;
    @Autowired
    private Validator validator;

    private Long ensureCategory() {
        return categoryRepository.findAll().stream().findFirst()
                .map(Category::getId)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("SKU Test Category " + System.nanoTime()).build()).getId());
    }

    private ProductCreateRequest baseRequest(String sku) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("SKU Test Item " + System.nanoTime());
        request.setSku(sku);
        request.setCategoryId(ensureCategory());
        request.setPurchasePrice(new BigDecimal("10"));
        request.setSellingPrice(new BigDecimal("15"));
        return request;
    }

    @Test
    void createProduct_uniqueSku_succeeds() {
        String sku = "SKU-UNIQUE-" + System.nanoTime();
        ProductResponse created = productService.createProduct(baseRequest(sku));
        assertThat(created.getSku()).isEqualTo(sku.toUpperCase());
    }

    @Test
    void createProduct_duplicateSku_caseInsensitive_isRejected() {
        String sku = "DUP-" + System.nanoTime();
        productService.createProduct(baseRequest(sku));

        ProductCreateRequest second = baseRequest(sku.toLowerCase());
        assertThatThrownBy(() -> productService.createProduct(second))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createProduct_skuIsTrimmedAndUppercased() {
        String rawSku = "  abc-" + System.nanoTime() + " ";
        ProductResponse created = productService.createProduct(baseRequest(rawSku));
        assertThat(created.getSku()).isEqualTo(rawSku.trim().toUpperCase());
        assertThat(created.getSku()).doesNotContain(" ");
    }

    @Test
    void createProduct_trailingWhitespaceVariant_isTreatedAsSameSku() {
        String sku = "PAD-" + System.nanoTime();
        productService.createProduct(baseRequest(sku));

        ProductCreateRequest padded = baseRequest("  " + sku + "  ");
        assertThatThrownBy(() -> productService.createProduct(padded))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createProductRequest_invalidSkuCharacters_failsBeanValidation() {
        ProductCreateRequest request = baseRequest("BAD SKU!#");
        Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
    }

    @Test
    void createProductRequest_skuTooLong_failsBeanValidation() {
        ProductCreateRequest request = baseRequest("A".repeat(51));
        Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
    }

    @Test
    void updateProduct_changeToUnusedSku_succeeds() {
        ProductResponse created = productService.createProduct(baseRequest("OLD-" + System.nanoTime()));
        String newSku = "NEW-" + System.nanoTime();

        ProductUpdateRequest update = toUpdateRequest(created, newSku);
        ProductResponse updated = productService.updateProduct(created.getId(), update);
        assertThat(updated.getSku()).isEqualTo(newSku.toUpperCase());
    }

    @Test
    void updateProduct_duplicateSkuOnUpdate_isRejected() {
        ProductResponse first = productService.createProduct(baseRequest("FIRST-" + System.nanoTime()));
        ProductResponse second = productService.createProduct(baseRequest("SECOND-" + System.nanoTime()));

        ProductUpdateRequest update = toUpdateRequest(second, first.getSku());
        assertThatThrownBy(() -> productService.updateProduct(second.getId(), update))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateProduct_skuChange_blockedOnceItemHasStockHistory() {
        ProductResponse created = productService.createProduct(baseRequest("HIST-" + System.nanoTime()));
        inventoryService.applyMovement(created.getId(), 10, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "Test stock-in");

        ProductUpdateRequest update = toUpdateRequest(created, "HIST-CHANGED-" + System.nanoTime());
        assertThatThrownBy(() -> productService.updateProduct(created.getId(), update))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("history");

        // Editing other fields (not the SKU) must still be allowed.
        ProductUpdateRequest sameSkuUpdate = toUpdateRequest(created, created.getSku());
        sameSkuUpdate.setSellingPrice(new BigDecimal("99"));
        ProductResponse updated = productService.updateProduct(created.getId(), sameSkuUpdate);
        assertThat(updated.getSellingPrice()).isEqualByComparingTo("99");
        assertThat(updated.getSku()).isEqualTo(created.getSku());
    }

    @Test
    void deactivatedProduct_retainsSku_andStaysSearchable() {
        ProductResponse created = productService.createProduct(baseRequest("DEACT-" + System.nanoTime()));
        productService.setStatus(created.getId(), ProductStatus.INACTIVE);

        ProductResponse fetched = productService.getProductById(created.getId());
        assertThat(fetched.getSku()).isEqualTo(created.getSku());
        assertThat(fetched.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(productRepository.findBySkuIgnoreCase(created.getSku())).isPresent();
    }

    @Test
    void search_findsProductBySku() {
        String sku = "SEARCH-" + System.nanoTime();
        productService.createProduct(baseRequest(sku));

        var page = productService.searchProducts(sku, null, null, 0, 10, "name", "asc");
        assertThat(page.getContent()).anyMatch(p -> p.getSku().equals(sku.toUpperCase()));
    }

    @Test
    void generateSku_producesSequentialItemFormat() {
        String first = skuGeneratorService.generateNext();
        String second = skuGeneratorService.generateNext();
        assertThat(first).matches("^ITEM-\\d{6}$");
        assertThat(second).matches("^ITEM-\\d{6}$");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateSku_concurrentCalls_neverDuplicate() throws InterruptedException {
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
                    results.add(skuGeneratorService.generateNext());
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
    void backfillMissingSkus_assignsSkuToLegacyNullSkuProduct() {
        Product legacy = productRepository.save(Product.builder()
                .name("Legacy No-SKU Item " + System.nanoTime())
                .sellingPrice(new BigDecimal("20"))
                .purchasePrice(new BigDecimal("15"))
                .status(ProductStatus.ACTIVE)
                .build());
        assertThat(legacy.getSku()).isNull();

        productService.backfillMissingSkus();

        Product reloaded = productRepository.findById(legacy.getId()).orElseThrow();
        assertThat(reloaded.getSku()).isNotBlank();
        assertThat(reloaded.getSku()).matches("^ITEM-\\d{6}$");
    }

    private ProductUpdateRequest toUpdateRequest(ProductResponse product, String sku) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName(product.getName());
        request.setSku(sku);
        request.setCategoryId(product.getCategoryId());
        request.setPurchasePrice(product.getPurchasePrice());
        request.setSellingPrice(product.getSellingPrice());
        return request;
    }
}
