package com.storehub.repository;

import com.storehub.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:stockStatus IS NULL OR :stockStatus = '' OR " +
            "  (:stockStatus = 'OUT_OF_STOCK' AND i.currentStock <= 0) OR " +
            "  (:stockStatus = 'LOW_STOCK' AND i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)) OR " +
            "  (:stockStatus = 'IN_STOCK' AND i.currentStock > COALESCE(p.minStockLevel, 0)))")
    Page<Inventory> search(@Param("search") String search,
                            @Param("categoryId") Long categoryId,
                            @Param("stockStatus") String stockStatus,
                            Pageable pageable);

    @Query("SELECT COALESCE(SUM(i.currentStock), 0) FROM Inventory i")
    long sumCurrentStock();

    @Query("SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)")
    long countLowStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.currentStock <= 0")
    long countOutOfStock();
}
