package com.storehub.repository;

import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByGstNumberIgnoreCase(String gstNumber);

    boolean existsByGstNumberIgnoreCaseAndIdNot(String gstNumber, Long id);

    @Query("SELECT s FROM Supplier s WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(s.mobile) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR s.status = :status)")
    Page<Supplier> search(@Param("search") String search,
                           @Param("status") SupplierStatus status,
                           Pageable pageable);
}
