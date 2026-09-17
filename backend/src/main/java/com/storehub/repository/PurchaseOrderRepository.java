package com.storehub.repository;

import com.storehub.entity.PurchaseOrder;
import com.storehub.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT o FROM PurchaseOrder o LEFT JOIN o.supplier s WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:supplierId IS NULL OR s.id = :supplierId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:fromDate IS NULL OR o.orderDate >= :fromDate) " +
            "AND (:toDate IS NULL OR o.orderDate <= :toDate) " +
            "AND (:storeId IS NULL OR o.store.id = :storeId)")
    Page<PurchaseOrder> search(@Param("search") String search,
                                @Param("supplierId") Long supplierId,
                                @Param("status") PurchaseOrderStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("storeId") Long storeId,
                                Pageable pageable);

    long countByStatusIn(java.util.Collection<PurchaseOrderStatus> statuses);
}
