package com.storehub.repository;

import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockHistory;
import com.storehub.entity.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    Page<StockHistory> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @Query("SELECT h FROM StockHistory h WHERE " +
            "(:productId IS NULL OR h.product.id = :productId) " +
            "AND (:movementType IS NULL OR h.movementType = :movementType) " +
            "AND (:referenceType IS NULL OR h.referenceType = :referenceType) " +
            "AND (:fromDateTime IS NULL OR h.createdAt >= :fromDateTime) " +
            "AND (:toDateTime IS NULL OR h.createdAt < :toDateTime)")
    Page<StockHistory> search(@Param("productId") Long productId,
                               @Param("movementType") StockMovementType movementType,
                               @Param("referenceType") ReferenceType referenceType,
                               @Param("fromDateTime") LocalDateTime fromDateTime,
                               @Param("toDateTime") LocalDateTime toDateTime,
                               Pageable pageable);
}
