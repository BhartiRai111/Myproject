package com.storehub.repository;

import com.storehub.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findByProductIdIn(List<Long> productIds);

    @Query("SELECT i FROM Inventory i JOIN i.product p LEFT JOIN p.category c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:stockStatus IS NULL OR :stockStatus = '' OR " +
            "  (:stockStatus = 'OUT_OF_STOCK' AND i.currentStock <= 0) OR " +
            "  (:stockStatus = 'LOW_STOCK' AND i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)) OR " +
            "  (:stockStatus = 'OVERSTOCK' AND i.maxStockLevel IS NOT NULL AND i.currentStock > i.maxStockLevel) OR " +
            "  (:stockStatus = 'IN_STOCK' AND i.currentStock > COALESCE(p.minStockLevel, 0) " +
            "    AND (i.maxStockLevel IS NULL OR i.currentStock <= i.maxStockLevel)))")
    Page<Inventory> search(@Param("search") String search,
                            @Param("categoryId") Long categoryId,
                            @Param("stockStatus") String stockStatus,
                            Pageable pageable);

    // Export-only variant: LEFT JOIN FETCH avoids one lazy-load query per row for
    // i.product/p.category when the caller (InventoryService.exportCsv) iterates the whole
    // unpaged result set.
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product p LEFT JOIN FETCH p.category c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:stockStatus IS NULL OR :stockStatus = '' OR " +
            "  (:stockStatus = 'OUT_OF_STOCK' AND i.currentStock <= 0) OR " +
            "  (:stockStatus = 'LOW_STOCK' AND i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)) OR " +
            "  (:stockStatus = 'OVERSTOCK' AND i.maxStockLevel IS NOT NULL AND i.currentStock > i.maxStockLevel) OR " +
            "  (:stockStatus = 'IN_STOCK' AND i.currentStock > COALESCE(p.minStockLevel, 0) " +
            "    AND (i.maxStockLevel IS NULL OR i.currentStock <= i.maxStockLevel)))")
    List<Inventory> search(@Param("search") String search,
                            @Param("categoryId") Long categoryId,
                            @Param("stockStatus") String stockStatus,
                            Sort sort);

    @Query("SELECT COALESCE(SUM(i.currentStock), 0) FROM Inventory i")
    long sumCurrentStock();

    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)")
    long countLowStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.currentStock <= 0")
    long countOutOfStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.maxStockLevel IS NOT NULL AND i.currentStock > i.maxStockLevel")
    long countOverstock();

    /** Products at or below their reorder point (spec section 18) — distinct from LOW_STOCK's minStockLevel threshold. */
    @Query("SELECT i FROM Inventory i JOIN i.product p WHERE p.reorderLevel IS NOT NULL AND i.currentStock <= p.reorderLevel " +
            "AND p.status = com.storehub.entity.ProductStatus.ACTIVE ORDER BY i.currentStock ASC")
    List<Inventory> findReorderCandidates();

    /** Count-only counterpart to {@link #findReorderCandidates()} — used by AlertService/getSummary, which only need the count. */
    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE p.reorderLevel IS NOT NULL AND i.currentStock <= p.reorderLevel " +
            "AND p.status = com.storehub.entity.ProductStatus.ACTIVE")
    long countReorderCandidates();
}
