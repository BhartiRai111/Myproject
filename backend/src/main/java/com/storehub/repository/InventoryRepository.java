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

    /** The one row for this product at this specific store (Multi-Store spec sections 7-9). */
    Optional<Inventory> findByProductIdAndStoreId(Long productId, Long storeId);

    /** Every store's row for this product — used only for cross-store aggregation, never displayed as "the" stock. */
    List<Inventory> findByProductId(Long productId);

    List<Inventory> findByStoreId(Long storeId);

    @Query("SELECT COALESCE(SUM(i.currentStock), 0) FROM Inventory i WHERE i.product.id = :productId")
    long sumCurrentStockForProduct(@Param("productId") Long productId);

    @Query("SELECT i.product.id AS productId, COALESCE(SUM(i.currentStock), 0) AS total FROM Inventory i " +
            "WHERE i.product.id IN :productIds GROUP BY i.product.id")
    List<Object[]> sumCurrentStockGroupedByProduct(@Param("productIds") List<Long> productIds);

    /** Store-scoped bulk lookup — one row per (product, store), used by store-filtered inventory/report views. */
    @Query("SELECT i.product.id AS productId, i.currentStock AS stock FROM Inventory i " +
            "WHERE i.product.id IN :productIds AND i.store.id = :storeId")
    List<Object[]> findCurrentStockByProductIdsAndStoreId(@Param("productIds") List<Long> productIds, @Param("storeId") Long storeId);

    @Query("SELECT i FROM Inventory i JOIN i.product p LEFT JOIN p.category c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:storeId IS NULL OR i.store.id = :storeId) " +
            "AND (:stockStatus IS NULL OR :stockStatus = '' OR " +
            "  (:stockStatus = 'OUT_OF_STOCK' AND i.currentStock <= 0) OR " +
            "  (:stockStatus = 'LOW_STOCK' AND i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)) OR " +
            "  (:stockStatus = 'OVERSTOCK' AND p.maxStockLevel IS NOT NULL AND i.currentStock > p.maxStockLevel) OR " +
            "  (:stockStatus = 'IN_STOCK' AND i.currentStock > COALESCE(p.minStockLevel, 0) " +
            "    AND (p.maxStockLevel IS NULL OR i.currentStock <= p.maxStockLevel)))")
    Page<Inventory> search(@Param("search") String search,
                            @Param("categoryId") Long categoryId,
                            @Param("storeId") Long storeId,
                            @Param("stockStatus") String stockStatus,
                            Pageable pageable);

    // Export-only variant: LEFT JOIN FETCH avoids one lazy-load query per row for
    // i.product/p.category when the caller (InventoryService.exportCsv) iterates the whole
    // unpaged result set.
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product p LEFT JOIN FETCH p.category c JOIN FETCH i.store WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:storeId IS NULL OR i.store.id = :storeId) " +
            "AND (:stockStatus IS NULL OR :stockStatus = '' OR " +
            "  (:stockStatus = 'OUT_OF_STOCK' AND i.currentStock <= 0) OR " +
            "  (:stockStatus = 'LOW_STOCK' AND i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)) OR " +
            "  (:stockStatus = 'OVERSTOCK' AND p.maxStockLevel IS NOT NULL AND i.currentStock > p.maxStockLevel) OR " +
            "  (:stockStatus = 'IN_STOCK' AND i.currentStock > COALESCE(p.minStockLevel, 0) " +
            "    AND (p.maxStockLevel IS NULL OR i.currentStock <= p.maxStockLevel)))")
    List<Inventory> search(@Param("search") String search,
                            @Param("categoryId") Long categoryId,
                            @Param("storeId") Long storeId,
                            @Param("stockStatus") String stockStatus,
                            Sort sort);

    @Query("SELECT COALESCE(SUM(i.currentStock), 0) FROM Inventory i WHERE (:storeId IS NULL OR i.store.id = :storeId)")
    long sumCurrentStock(@Param("storeId") Long storeId);

    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0) " +
            "AND (:storeId IS NULL OR i.store.id = :storeId)")
    long countLowStock(@Param("storeId") Long storeId);

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.currentStock <= 0 AND (:storeId IS NULL OR i.store.id = :storeId)")
    long countOutOfStock(@Param("storeId") Long storeId);

    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE p.maxStockLevel IS NOT NULL AND i.currentStock > p.maxStockLevel " +
            "AND (:storeId IS NULL OR i.store.id = :storeId)")
    long countOverstock(@Param("storeId") Long storeId);

    /** Products at or below their reorder point (spec section 18) — distinct from LOW_STOCK's minStockLevel threshold. */
    @Query("SELECT i FROM Inventory i JOIN i.product p WHERE p.reorderLevel IS NOT NULL AND i.currentStock <= p.reorderLevel " +
            "AND p.status = com.storehub.entity.ProductStatus.ACTIVE AND (:storeId IS NULL OR i.store.id = :storeId) ORDER BY i.currentStock ASC")
    List<Inventory> findReorderCandidates(@Param("storeId") Long storeId);

    /** Count-only counterpart to {@link #findReorderCandidates(Long)} — used by AlertService/getSummary, which only need the count. */
    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE p.reorderLevel IS NOT NULL AND i.currentStock <= p.reorderLevel " +
            "AND p.status = com.storehub.entity.ProductStatus.ACTIVE AND (:storeId IS NULL OR i.store.id = :storeId)")
    long countReorderCandidates(@Param("storeId") Long storeId);
}
