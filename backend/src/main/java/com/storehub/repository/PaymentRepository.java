package com.storehub.repository;

import com.storehub.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p LEFT JOIN p.supplier s WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.paymentNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:supplierId IS NULL OR s.id = :supplierId) " +
            "AND (:fromDate IS NULL OR p.paymentDate >= :fromDate) " +
            "AND (:toDate IS NULL OR p.paymentDate <= :toDate)")
    Page<Payment> search(@Param("search") String search,
                          @Param("supplierId") Long supplierId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          Pageable pageable);
}
