package com.storehub.repository;

import com.storehub.entity.ItemGroup;
import com.storehub.entity.ItemGroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemGroupRepository extends JpaRepository<ItemGroup, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT g FROM ItemGroup g WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR g.status = :status)")
    Page<ItemGroup> search(@Param("search") String search, @Param("status") ItemGroupStatus status, Pageable pageable);
}
