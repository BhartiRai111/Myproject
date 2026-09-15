package com.storehub.repository;

import com.storehub.entity.CashTransaction;
import com.storehub.entity.CashTransactionStatus;
import com.storehub.entity.CashTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    @Query("SELECT c FROM CashTransaction c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(c.transactionNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.reason) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:transactionType IS NULL OR c.transactionType = :transactionType) " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:fromDate IS NULL OR c.transactionDate >= :fromDate) " +
            "AND (:toDate IS NULL OR c.transactionDate <= :toDate)")
    Page<CashTransaction> search(@Param("search") String search,
                                  @Param("transactionType") CashTransactionType transactionType,
                                  @Param("status") CashTransactionStatus status,
                                  @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                  Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashTransaction c WHERE c.transactionDate = :date " +
            "AND c.transactionType = :type AND c.status = com.storehub.entity.CashTransactionStatus.POSTED")
    java.math.BigDecimal sumForDateAndType(@Param("date") LocalDate date, @Param("type") CashTransactionType type);
}
