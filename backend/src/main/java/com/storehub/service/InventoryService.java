package com.storehub.service;

import com.storehub.dto.InventoryResponse;
import com.storehub.dto.InventorySummaryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockAdjustmentRequest;
import com.storehub.dto.StockHistoryResponse;
import com.storehub.entity.Inventory;
import com.storehub.entity.Product;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockHistory;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockStatus;
import com.storehub.entity.User;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.InventoryNotFoundException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.StockHistoryRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Set<StockMovementType> MANUAL_MOVEMENT_TYPES =
            Set.of(StockMovementType.STOCK_IN, StockMovementType.STOCK_OUT, StockMovementType.ADJUSTMENT);

    private static final Set<String> SORTABLE_FIELDS = Set.of("currentStock", "updatedAt");

    private final InventoryRepository inventoryRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- used by ProductService: keep Product APIs backed by Inventory as the single source of truth ----

    @Transactional
    public void createInventoryForProduct(Product product) {
        inventoryRepository.save(Inventory.builder().product(product).currentStock(0).build());
    }

    public int getCurrentStock(Long productId) {
        return inventoryRepository.findByProductId(productId).map(Inventory::getCurrentStock).orElse(0);
    }

    public Map<Long, Integer> getCurrentStockBulk(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Inventory::getCurrentStock));
    }

    // ---- inventory list / detail / summary ----

    public PagedResponse<InventoryResponse> searchInventory(String search, Long categoryId, StockStatus stockStatus,
                                                             int page, int size, String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "updatedAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(field).ascending() : Sort.by(field).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        String statusParam = stockStatus != null ? stockStatus.name() : null;
        Page<Inventory> result = inventoryRepository.search(search, categoryId, statusParam, pageable);
        return PagedResponse.fromPage(result.map(InventoryResponse::fromEntity));
    }

    public InventoryResponse getInventoryById(Long id) {
        return InventoryResponse.fromEntity(findInventoryOrThrow(id));
    }

    public InventorySummaryResponse getSummary() {
        return InventorySummaryResponse.builder()
                .totalProducts(inventoryRepository.count())
                .totalStockUnits(inventoryRepository.sumCurrentStock())
                .lowStockCount(inventoryRepository.countLowStock())
                .outOfStockCount(inventoryRepository.countOutOfStock())
                .build();
    }

    // ---- stock history ----

    public PagedResponse<StockHistoryResponse> getHistoryForInventory(Long inventoryId, int page, int size) {
        Inventory inventory = findInventoryOrThrow(inventoryId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockHistoryResponse> result = stockHistoryRepository
                .findByProductIdOrderByCreatedAtDesc(inventory.getProduct().getId(), pageable)
                .map(StockHistoryResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public PagedResponse<StockHistoryResponse> searchHistory(Long productId, StockMovementType movementType,
                                                              ReferenceType referenceType, LocalDate fromDate,
                                                              LocalDate toDate, int page, int size) {
        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockHistoryResponse> result = stockHistoryRepository
                .search(productId, movementType, referenceType, fromDateTime, toDateTime, pageable)
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
        Inventory inventory = getOrCreateInventory(product);

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
                    + "': current stock is " + previousStock + ", requested " + request.getMovementType()
                    + " of " + request.getQuantity());
        }

        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        stockHistoryRepository.save(StockHistory.builder()
                .product(product)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .previousStock(previousStock)
                .newStock(newStock)
                .reason(request.getReason())
                .referenceType(ReferenceType.MANUAL)
                .notes(request.getNotes())
                .createdBy(currentUsername())
                .build());

        return InventoryResponse.fromEntity(inventory);
    }

    // ---- system-triggered stock movement (Purchase/Sale integration) ----

    @Transactional
    public void applyMovement(Long productId, int delta, StockMovementType movementType,
                               ReferenceType referenceType, Long referenceId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        Inventory inventory = getOrCreateInventory(product);

        int previousStock = inventory.getCurrentStock();
        int newStock = previousStock + delta;
        if (newStock < 0) {
            throw new BadRequestException("Insufficient stock for product '" + product.getName() + "'");
        }

        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        stockHistoryRepository.save(StockHistory.builder()
                .product(product)
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

    @Transactional
    public void backfillInventoryForExistingProducts() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return;
        }

        Map<Long, Integer> legacyStock = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT id, stock_quantity FROM products").getResultList();
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            Integer quantity = row[1] != null ? ((Number) row[1]).intValue() : 0;
            legacyStock.put(id, quantity);
        }

        for (Product product : products) {
            if (inventoryRepository.findByProductId(product.getId()).isEmpty()) {
                Inventory inventory = Inventory.builder()
                        .product(product)
                        .currentStock(legacyStock.getOrDefault(product.getId(), 0))
                        .build();
                inventoryRepository.save(inventory);
            }
        }
    }

    private Inventory getOrCreateInventory(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder().product(product).currentStock(0).build()));
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
