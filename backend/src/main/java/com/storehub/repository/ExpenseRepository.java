package com.storehub.repository;

import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.PaymentMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(e.expenseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.vendorName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR e.status = :status) " +
            "AND (:category IS NULL OR :category = '' OR e.category = :category) " +
            "AND (:categoryId IS NULL OR e.expenseCategory.id = :categoryId) " +
            "AND (:supplierId IS NULL OR e.supplier.id = :supplierId) " +
            "AND (:paymentMode IS NULL OR e.paymentMode = :paymentMode) " +
            "AND (:gstApplicable IS NULL OR (:gstApplicable = true AND e.taxMode IS NOT NULL) " +
            "     OR (:gstApplicable = false AND e.taxMode IS NULL)) " +
            "AND (:itcEligible IS NULL OR e.itcEligible = :itcEligible) " +
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR e.expenseDate <= :toDate)")
    Page<Expense> search(@Param("search") String search, @Param("status") ExpenseStatus status,
                          @Param("category") String category,
                          @Param("categoryId") Long categoryId,
                          @Param("supplierId") Long supplierId,
                          @Param("paymentMode") PaymentMode paymentMode,
                          @Param("gstApplicable") Boolean gstApplicable,
                          @Param("itcEligible") Boolean itcEligible,
                          @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, Pageable pageable);

    @Query("SELECT e.id FROM Expense e WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED")
    List<Long> findPostedIds();

    @Query("SELECT e.id FROM Expense e")
    List<Long> findAllIds();

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.expenseDate = :date AND e.status = com.storehub.entity.ExpenseStatus.POSTED")
    BigDecimal getTotalExpensesForDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.expenseDate BETWEEN :fromDate AND :toDate AND e.status = com.storehub.entity.ExpenseStatus.POSTED")
    BigDecimal sumTotalAmountByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Every POSTED credit (party) expense still owed to this supplier — the Expense-side analogue of PurchaseRepository.findOutstandingBySupplier. */
    @Query("SELECT e FROM Expense e WHERE e.supplier.id = :supplierId AND e.payableAmount > 0 " +
            "AND e.status = com.storehub.entity.ExpenseStatus.POSTED ORDER BY e.expenseDate ASC, e.id ASC")
    List<Expense> findOutstandingBySupplier(@Param("supplierId") Long supplierId);

    // ---- Expense Summary report aggregations (server-side; never load every row into React) ----

    @Query("SELECT e.category, COALESCE(SUM(e.totalAmount),0), COUNT(e) FROM Expense e " +
            "WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED " +
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) AND (:toDate IS NULL OR e.expenseDate <= :toDate) " +
            "GROUP BY e.category ORDER BY SUM(e.totalAmount) DESC")
    List<Object[]> sumByCategory(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT e.paymentMode, COALESCE(SUM(e.totalAmount),0), COUNT(e) FROM Expense e " +
            "WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED " +
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) AND (:toDate IS NULL OR e.expenseDate <= :toDate) " +
            "GROUP BY e.paymentMode ORDER BY SUM(e.totalAmount) DESC")
    List<Object[]> sumByPaymentMode(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT e.supplier.id, e.supplier.name, COALESCE(SUM(e.totalAmount),0), COUNT(e) FROM Expense e " +
            "WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED AND e.supplier IS NOT NULL " +
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) AND (:toDate IS NULL OR e.expenseDate <= :toDate) " +
            "GROUP BY e.supplier.id, e.supplier.name ORDER BY SUM(e.totalAmount) DESC")
    List<Object[]> sumByParty(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN e.taxMode IS NOT NULL THEN e.totalAmount ELSE 0 END),0), " +
            "COALESCE(SUM(CASE WHEN e.taxMode IS NULL THEN e.totalAmount ELSE 0 END),0), " +
            "COALESCE(SUM(CASE WHEN e.itcEligible = true THEN e.cgstAmount + e.sgstAmount + e.igstAmount ELSE 0 END),0), " +
            "COALESCE(SUM(CASE WHEN e.taxMode IS NOT NULL AND e.itcEligible = false THEN e.cgstAmount + e.sgstAmount + e.igstAmount ELSE 0 END),0), " +
            "COALESCE(SUM(e.totalAmount),0), COUNT(e) " +
            "FROM Expense e WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED " +
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) AND (:toDate IS NULL OR e.expenseDate <= :toDate)")
    List<Object[]> gstSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
