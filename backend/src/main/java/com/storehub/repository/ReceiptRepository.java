package com.storehub.repository;

import com.storehub.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    @Query("SELECT r FROM Receipt r LEFT JOIN r.customer c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(r.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:customerId IS NULL OR c.id = :customerId) " +
            "AND (:fromDate IS NULL OR r.receiptDate >= :fromDate) " +
            "AND (:toDate IS NULL OR r.receiptDate <= :toDate)")
    Page<Receipt> search(@Param("search") String search,
                          @Param("customerId") Long customerId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          Pageable pageable);
}
