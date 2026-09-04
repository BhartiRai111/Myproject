package com.storehub.repository;

import com.storehub.entity.Unit;
import com.storehub.entity.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySymbolIgnoreCase(String symbol);

    boolean existsBySymbolIgnoreCaseAndIdNot(String symbol, Long id);

    @Query("SELECT u FROM Unit u WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.symbol) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR u.status = :status)")
    Page<Unit> search(@Param("search") String search, @Param("status") UnitStatus status, Pageable pageable);
}
