package com.storehub.repository;

import com.storehub.entity.Hsn;
import com.storehub.entity.HsnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HsnRepository extends JpaRepository<Hsn, Long> {

    boolean existsByHsnCodeIgnoreCase(String hsnCode);

    boolean existsByHsnCodeIgnoreCaseAndIdNot(String hsnCode, Long id);

    @Query("SELECT h FROM Hsn h WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(h.hsnCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(h.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR h.status = :status)")
    Page<Hsn> search(@Param("search") String search, @Param("status") HsnStatus status, Pageable pageable);
}
