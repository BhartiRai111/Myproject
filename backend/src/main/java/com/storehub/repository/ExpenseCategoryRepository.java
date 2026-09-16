package com.storehub.repository;

import com.storehub.entity.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<ExpenseCategory> findByActiveTrueOrderByNameAsc();

    @Query("SELECT c FROM ExpenseCategory c WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:active IS NULL OR c.active = :active) " +
            "ORDER BY c.name ASC")
    Page<ExpenseCategory> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}
