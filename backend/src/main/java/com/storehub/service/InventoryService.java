package com.storehub.service;

import com.storehub.dto.InventoryResponse;
import com.storehub.dto.InventorySummaryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockAdjustmentRequest;
import com.storehub.dto.StockHistoryResponse;
import com.storehub.entity.Inventory;
import com.storehub.entity.Product;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.Store;
import com.storehub.entity.StoreStatus;
import com.storehub.entity.StockHistory;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockStatus;
import com.storehub.entity.User;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.InventoryNotFoundException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.StockHistoryRepository;
import com.storehub.repository.StoreRepository;
import com.storehub.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for stock — and, since Multi-Store (spec sections 7-9, 15),
 * for stock <em>per store</em>: one {@link Inventory} row per (product, store) rather than
 * one per product. Every method that reads/writes an actual stock quantity takes an
 * explicit storeId; the no-storeId overloads that remain are deliberately aggregate-only
 * (sum across every store a product has a row for) for global Item Master views that are
 * not yet store-scoped (spec section 15's "any global stock display must be derived from
 * store-level rows").
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Set<StockMovementType> MANUAL_MOVEMENT_TYPES =
            Set.of(StockMovementType.STOCK_IN, StockMovementType.STOCK_OUT, StockMovementType.ADJUSTMENT);

    private static final Set<String> SORTABLE_FIELDS = Set.of("currentStock", "updatedAt");

    private final InventoryRepository inventoryRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- current stock: store-specific (primary) and cross-store aggregate (for global views) ----

    /** Stock of this product at this specific store — the only figure a store-scoped transaction may ever act on. */
    public int getCurrentStock(Long productId, Long storeId) {
        return inventoryRepository.findByProductIdAndStoreId(productId, storeId).map(Inventory::getCurrentStock).orElse(0);
    }

    /** Aggregate stock across every store — for global Item Master list/summary displays only, never for transaction validation. */
    public int getCurrentStock(Long productId) {
        return (int) inventoryRepository.sumCurrentStockForProduct(productId);
    }

    /**
     * Bulk aggregate equivalent of {@link #getCurrentStock(Long)} — avoids one query per
     * product for the Products list / CSV export over a full catalog.
     */
    public Map<Long, Integer> getCurrentStockBulk(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : inventoryRepository.sumCurrentStockGroupedByProduct(productIds)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return result;
    }

    /** Bulk store-scoped equivalent — for a store-filtered inventory/report view over many products at once. */
    public Map<Long, Integer> getCurrentStockBulk(List<Long> productIds, Long storeId) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : inventoryRepository.findCurrentStockByProductIdsAndStoreId(productIds, storeId)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return result;
    }

    /**
     * Creates the baseline (0-stock) Inventory row for a newly created Product, attributed
     * to the Default Store — an Item Master creation is global master data with no store
     * of its own yet, so the Default Store is the same historical-data-safety anchor used
     * everywhere else in the Multi-Store migration (spec section 76). Mirrors the single
     * 0-stock row this always created pre-Multi-Store; a later store-aware movement
     * (Purchase, Stock Transfer, adjustment) creates whichever real (product, store) row
     * it actually needs, on demand, via {@link #getOrCreateInventory}.
     */
    @Transactional
    public void createInventoryForProduct(Product product) {
        Store defaultStore = storeService.getOrCreateDefaultStore();
        if (inventoryRepository.findByProductIdAndStoreId(product.getId(), defaultStore.getId()).isEmpty()) {
            inventoryRepository.save(Inventory.builder().product(product).store(defaultStore).currentStock(0).build());
        }
    }

    // ---- inventory list / detail / summary ----

    public PagedResponse<InventoryResponse> searchInventory(String search, Long categoryId, Long storeId, StockStatus stockStatus,
                                                             int page, int size, String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "updatedAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(field).ascending() : Sort.by(field).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        String statusParam = stockStatus != null ? stockStatus.name() : null;
        Page<Inventory> result = inventoryRepository.search(search, categoryId, storeId, statusParam, pageable);
        return PagedResponse.fromPage(result.map(InventoryResponse::fromEntity));
    }

    public InventoryResponse getInventoryById(Long id) {
        return InventoryResponse.fromEntity(findInventoryOrThrow(id));
    }

    private static final List<String> EXPORT_HEADER = List.of(
            "store", "product", "sku", "category", "unit", "currentStock", "minStockLevel", "maxStockLevel",
            "reorderLevel", "reorderQuantity", "stockStatus", "lastUpdated");

    /** Exports the same search/category/store/status-filtered inventory list the Inventory page shows. */
    public String exportCsv(String search, Long categoryId, Long storeId, StockStatus stockStatus) {
        String statusParam = stockStatus != null ? stockStatus.name() : null;
        List<Inventory> rows = inventoryRepository.search(search, categoryId, storeId, statusParam, Sort.by("product.name").ascending());

        StringBuilder csv = new StringBuilder();
        csv.append(com.storehub.util.CsvUtil.row(EXPORT_HEADER.toArray()));
        for (Inventory inv : rows) {
            InventoryResponse r = InventoryResponse.fromEntity(inv);
            csv.append(com.storehub.util.CsvUtil.row(
                    r.getStoreName(), r.getProductName(), r.getSku(), r.getCategoryName(), r.getUnit(), r.getCurrentStock(),
                    r.getMinStockLevel(), r.getMaxStockLevel(), r.getReorderLevel(), r.getReorderQuantity(),
                    r.getStockStatus(), r.getLastUpdated()));
        }
        return csv.toString();
    }

    /** storeId null = aggregate across every accessible store (ALL_STORES admin view). */
    public InventorySummaryResponse getSummary(Long storeId) {
        return InventorySummaryResponse.builder()
                .totalProducts(storeId != null ? inventoryRepository.findByStoreId(storeId).size() : inventoryRepository.count())
                .totalStockUnits(inventoryRepository.sumCurrentStock(storeId))
                .lowStockCount(inventoryRepository.countLowStock(storeId))
                .outOfStockCount(inventoryRepository.countOutOfStock(storeId))
                .overstockCount(inventoryRepository.countOverstock(storeId))
                .reorderCandidateCount(inventoryRepository.countReorderCandidates(storeId))
                .build();
    }

    /** Products at or below their configured reorder point — used by the Alert Center (Phase 6). storeId null = every store. */
    public List<Inventory> getReorderCandidates(Long storeId) {
        return inventoryRepository.findReorderCandidates(storeId);
    }

    // ---- stock history ----

    public PagedResponse<StockHistoryResponse> getHistoryForInventory(Long inventoryId, int page, int size) {
        Inventory inventory = findInventoryOrThrow(inventoryId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockHistoryResponse> result = stockHistoryRepository
                .search(inventory.getProduct().getId(), inventory.getStore().getId(), null, null, null, null, pageable)
                .map(StockHistoryResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public PagedResponse<StockHistoryResponse> searchHistory(Long productId, Long storeId, StockMovementType movementType,
                                                              ReferenceType referenceType, LocalDate fromDate,
                                                              LocalDate toDate, int page, int size) {
        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockHistoryResponse> result = stockHistoryRepository
                .search(productId, storeId, movementType, referenceType, fromDateTime, toDateTime, pageable)
                .map(StockHistoryResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    // ---- manual stock adjustment (user-facing) ----

    @Transactional
    public InventoryResponse adjustStock(StockAdjustmentRequest request) {
        if (!MANUAL_MOVEMENT_TYPES.contains(request.getMovementType())) {
            throw new BadRequestException("Adjustment type must be one of STOCK_IN, STOCK_OUT, or ADJUSTMENT");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));
        Store store = resolveActiveStore(request.getStoreId());
        Inventory inventory = getOrCreateInventory(product, store);

        int previousStock = inventory.getCurrentStock();
        int newStock;
        switch (request.getMovementType()) {
            case STOCK_IN -> newStock = previousStock + request.getQuantity();
            case STOCK_OUT -> newStock = previousStock - request.getQuantity();
            case ADJUSTMENT -> newStock = request.getQuantity();
            default -> throw new BadRequestException("Unsupported adjustment type");
        }

        if (newStock < 0) {
            throw new BadRequestException("This adjustment would make stock negative for product '" + product.getName()
                    + "' at store '" + store.getStoreName() + "': current stock is " + previousStock + ", requested "
                    + request.getMovementType() + " of " + request.getQuantity());
        }

        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        stockHistoryRepository.save(StockHistory.builder()
                .product(product)
                .store(store)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .previousStock(previousStock)
                .newStock(newStock)
                .reason(request.getReason())
                .referenceType(ReferenceType.MANUAL)
                .notes(request.getNotes())
                .createdBy(currentUsername())
                .build());

        auditService.log(com.storehub.entity.AuditAction.UPDATE, "INVENTORY", "Product", product.getId(), product.getSku(),
                String.valueOf(previousStock), String.valueOf(newStock),
                "Stock adjustment (" + request.getMovementType() + ") on '" + product.getName() + "' at store '"
                        + store.getStoreCode() + "': " + request.getReason(), store.getId());

        return InventoryResponse.fromEntity(inventory);
    }

    // ---- system-triggered stock movement (Purchase/Sale integration) ----

    /**
     * Store-aware primary movement method — the only one a store-attributed transaction
     * (Sale, Purchase, Credit/Debit Note) should call once it has its own storeId (spec
     * sections 16-17). Rejects a movement against an INACTIVE store (spec section 6).
     */
    @Transactional
    public void applyMovement(Long productId, Long storeId, int delta, StockMovementType movementType,
                               ReferenceType referenceType, Long referenceId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        Store store = resolveActiveStore(storeId);
        Inventory inventory = getOrCreateInventory(product, store);

        int previousStock = inventory.getCurrentStock();
        int newStock = previousStock + delta;
        if (newStock < 0) {
            throw new BadRequestException("Insufficient stock for product '" + product.getName() + "' at store '" + store.getStoreName() + "'");
        }

        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        stockHistoryRepository.save(StockHistory.builder()
                .product(product)
                .store(store)
                .movementType(movementType)
                .quantity(Math.abs(delta))
                .previousStock(previousStock)
                .newStock(newStock)
                .reason(reason)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdBy(currentUsername())
                .build());
    }

    /**
     * Transitional overload for call sites (Sale/Purchase/Credit Note/Debit Note services)
     * that do not yet carry their own storeId — attributes the movement to the Default
     * Store (spec section 76's historical-data-safety anchor) so behavior is unchanged
     * from the pre-Multi-Store single-store model until each of those flows is migrated
     * to pass its own resolved storeId.
     */
    @Transactional
    public void applyMovement(Long productId, int delta, StockMovementType movementType,
                               ReferenceType referenceType, Long referenceId, String reason) {
        applyMovement(productId, storeService.getOrCreateDefaultStore().getId(), delta, movementType, referenceType, referenceId, reason);
    }

    // ---- one-time startup migration: seed Inventory from the legacy Product.stock_quantity column ----

    /**
     * The legacy products.stock_quantity column was left in the database (never dropped, per
     * migration policy) when Inventory became the source of truth, but it is still NOT NULL with
     * no default from its original mapping. Since Product no longer maps this column, Hibernate
     * omits it from INSERTs, which MySQL then rejects (error 1364). Relaxing it here is idempotent
     * and safe to run on every startup.
     */
    @Transactional
    public void relaxLegacyStockQuantityColumn() {
        try {
            entityManager.createNativeQuery("ALTER TABLE products MODIFY stock_quantity INT NULL DEFAULT 0").executeUpdate();
        } catch (Exception ignored) {
            // Column already relaxed, or DDL not permitted in this environment; safe to continue.
        }
    }

    /**
     * Before Multi-Store, {@code inventory.product_id} carried its own single-column
     * UNIQUE constraint (one row per product). {@code ddl-auto=update} never drops a
     * constraint it once created, so that leftover would otherwise still reject a second
     * store's row for the same product once real multi-store usage starts. Dropped here
     * via information_schema (its Hibernate-generated name is unpredictable) rather than
     * a hardcoded name; best-effort and idempotent, matching the sibling
     * {@link #relaxLegacyStockQuantityColumn()} pattern.
     */
    @Transactional
    public void relaxLegacyInventoryProductUniqueConstraint() {
        try {
            @SuppressWarnings("unchecked")
            List<Object> legacyIndexNames = entityManager.createNativeQuery(
                    "SELECT DISTINCT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory' AND COLUMN_NAME = 'product_id' " +
                            "AND NON_UNIQUE = 0 AND INDEX_NAME <> 'uk_inventory_product_store'").getResultList();
            for (Object indexName : legacyIndexNames) {
                entityManager.createNativeQuery("ALTER TABLE inventory DROP INDEX `" + indexName + "`").executeUpdate();
            }
        } catch (Exception ignored) {
            // Legacy constraint already removed, table doesn't exist yet, or DDL not permitted; safe to continue.
        }
    }

    /**
     * Pre-Multi-Store products had exactly one implicit stock row with no store; backfilled
     * onto the Default Store (spec section 76) — both any row Hibernate already added a
     * (nullable) store_id column to and left null, and any product with no Inventory row
     * at all yet.
     */
    @Transactional
    public void backfillInventoryForExistingProducts() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return;
        }

        Store defaultStore = storeService.getOrCreateDefaultStore();

        Map<Long, Integer> legacyStock = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT id, stock_quantity FROM products").getResultList();
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            Integer quantity = row[1] != null ? ((Number) row[1]).intValue() : 0;
            legacyStock.put(id, quantity);
        }

        for (Product product : products) {
            List<Inventory> existingRows = inventoryRepository.findByProductId(product.getId());
            if (existingRows.isEmpty()) {
                Inventory inventory = Inventory.builder()
                        .product(product)
                        .store(defaultStore)
                        .currentStock(legacyStock.getOrDefault(product.getId(), 0))
                        .build();
                inventoryRepository.save(inventory);
                continue;
            }
            for (Inventory inventory : existingRows) {
                if (inventory.getStore() == null) {
                    inventory.setStore(defaultStore);
                    inventoryRepository.save(inventory);
                }
            }
        }
    }

    private Inventory getOrCreateInventory(Product product, Store store) {
        return inventoryRepository.findByProductIdAndStoreId(product.getId(), store.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder().product(product).store(store).currentStock(0).build()));
    }

    private Store resolveActiveStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new MasterNotFoundException("Store", storeId));
        if (store.getStatus() == StoreStatus.INACTIVE) {
            throw new BadRequestException("Store '" + store.getStoreName() + "' is inactive and cannot receive new stock movements");
        }
        return store;
    }

    private Inventory findInventoryOrThrow(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new InventoryNotFoundException(id));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            User user = principal.getUser();
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            return (user.getFirstName() + " " + lastName).trim();
        }
        return "System";
    }
}
