package com.storehub.repository;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("SELECT s FROM Sale s LEFT JOIN s.customer c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(s.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:paymentStatus IS NULL OR s.paymentStatus = :paymentStatus) " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:fromDate IS NULL OR s.saleDate >= :fromDate) " +
            "AND (:toDate IS NULL OR s.saleDate <= :toDate)")
    Page<Sale> search(@Param("search") String search,
                       @Param("paymentStatus") PaymentStatus paymentStatus,
                       @Param("status") SaleStatus status,
                       @Param("fromDate") LocalDate fromDate,
                       @Param("toDate") LocalDate toDate,
                       Pageable pageable);
}
