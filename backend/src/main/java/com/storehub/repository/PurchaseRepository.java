package com.storehub.repository;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    @Query("SELECT p FROM Purchase p WHERE p.supplier.id = :supplierId AND p.payableAmount > 0 " +
            "AND p.status <> com.storehub.entity.PurchaseStatus.CANCELLED ORDER BY p.purchaseDate ASC, p.id ASC")
    List<Purchase> findOutstandingBySupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Purchase p WHERE p.purchaseDate = :date " +
            "AND p.status <> com.storehub.entity.PurchaseStatus.CANCELLED")
    java.math.BigDecimal getTotalPurchasesForDate(@Param("date") LocalDate date);

    @Query("SELECT p FROM Purchase p WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.purchaseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.supplier.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:paymentStatus IS NULL OR p.paymentStatus = :paymentStatus) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:fromDate IS NULL OR p.purchaseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR p.purchaseDate <= :toDate)")
    Page<Purchase> search(@Param("search") String search,
                           @Param("paymentStatus") PaymentStatus paymentStatus,
                           @Param("status") PurchaseStatus status,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate,
                           Pageable pageable);
}
