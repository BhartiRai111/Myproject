package com.storehub.repository;

import com.storehub.entity.Zone;
import com.storehub.entity.ZoneStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT z FROM Zone z WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(z.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR z.status = :status)")
    Page<Zone> search(@Param("search") String search, @Param("status") ZoneStatus status, Pageable pageable);
}
