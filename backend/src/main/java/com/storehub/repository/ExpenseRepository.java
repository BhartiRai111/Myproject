package com.storehub.repository;

import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseStatus;
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
            "AND (:fromDate IS NULL OR e.expenseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR e.expenseDate <= :toDate)")
    Page<Expense> search(@Param("search") String search, @Param("status") ExpenseStatus status,
                          @Param("category") String category,
                          @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, Pageable pageable);

    @Query("SELECT e.id FROM Expense e WHERE e.status = com.storehub.entity.ExpenseStatus.POSTED")
    List<Long> findPostedIds();

    @Query("SELECT e.id FROM Expense e")
    List<Long> findAllIds();

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.expenseDate = :date AND e.status = com.storehub.entity.ExpenseStatus.POSTED")
    BigDecimal getTotalExpensesForDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM Expense e WHERE e.expenseDate BETWEEN :fromDate AND :toDate AND e.status = com.storehub.entity.ExpenseStatus.POSTED")
    BigDecimal sumTotalAmountByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
