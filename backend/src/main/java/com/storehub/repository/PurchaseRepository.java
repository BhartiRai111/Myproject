package com.storehub.repository;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.TransactionType;
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

    /** Every outstanding bill across all suppliers, for the Outstanding Bill / Ageing report — never DRAFT/CANCELLED. */
    @Query("SELECT p FROM Purchase p WHERE p.payableAmount > 0 " +
            "AND p.status = com.storehub.entity.PurchaseStatus.COMPLETED ORDER BY p.purchaseDate ASC, p.id ASC")
    List<Purchase> findAllOutstanding();

    @Query("SELECT p.id FROM Purchase p WHERE p.status = com.storehub.entity.PurchaseStatus.COMPLETED")
    List<Long> findCompletedIds();

    @Query("SELECT p.id FROM Purchase p")
    List<Long> findAllIds();

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
            "AND (:toDate IS NULL OR p.purchaseDate <= :toDate) " +
            "AND (:transactionType IS NULL OR p.transactionType = :transactionType)")
    Page<Purchase> search(@Param("search") String search,
                           @Param("paymentStatus") PaymentStatus paymentStatus,
                           @Param("status") PurchaseStatus status,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate,
                           @Param("transactionType") TransactionType transactionType,
                           Pageable pageable);

    /**
     * Lightweight projection of every purchase eligible for GST reporting in a date range
     * (id, purchaseNumber, purchaseDate, taxableAmount, cgstAmount, sgstAmount, igstAmount, totalAmount),
     * for the GST Reconciliation report. Never loads full entities/relations.
     */
    @Query("SELECT p.id, p.purchaseNumber, p.purchaseDate, p.taxableAmount, p.cgstAmount, p.sgstAmount, p.igstAmount, p.totalAmount " +
            "FROM Purchase p WHERE p.status = com.storehub.entity.PurchaseStatus.COMPLETED " +
            "AND p.gstReportingApplicable = true " +
            "AND (:fromDate IS NULL OR p.purchaseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR p.purchaseDate <= :toDate) " +
            "ORDER BY p.purchaseDate ASC, p.id ASC")
    List<Object[]> findEligibleForReconciliation(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
