package com.storehub.repository;

import com.storehub.entity.SalesOrder;
import com.storehub.entity.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Query("SELECT o FROM SalesOrder o LEFT JOIN o.customer c WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:customerId IS NULL OR c.id = :customerId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:fromDate IS NULL OR o.orderDate >= :fromDate) " +
            "AND (:toDate IS NULL OR o.orderDate <= :toDate)")
    Page<SalesOrder> search(@Param("search") String search,
                             @Param("customerId") Long customerId,
                             @Param("status") SalesOrderStatus status,
                             @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate,
                             Pageable pageable);

    long countByStatusIn(java.util.Collection<SalesOrderStatus> statuses);
}
