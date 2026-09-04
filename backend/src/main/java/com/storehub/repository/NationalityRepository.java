package com.storehub.repository;

import com.storehub.entity.Nationality;
import com.storehub.entity.NationalityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NationalityRepository extends JpaRepository<Nationality, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT n FROM Nationality n JOIN n.country c WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(n.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:countryId IS NULL OR c.id = :countryId) " +
            "AND (:status IS NULL OR n.status = :status)")
    Page<Nationality> search(@Param("search") String search, @Param("countryId") Long countryId,
                              @Param("status") NationalityStatus status, Pageable pageable);
}
