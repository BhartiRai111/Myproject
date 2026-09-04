package com.storehub.repository;

import com.storehub.entity.City;
import com.storehub.entity.CityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<City, Long> {

    boolean existsByNameIgnoreCaseAndStateId(String name, Long stateId);

    boolean existsByNameIgnoreCaseAndStateIdAndIdNot(String name, Long stateId, Long id);

    @Query("SELECT ci FROM City ci JOIN ci.state s JOIN s.country c WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(ci.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:stateId IS NULL OR s.id = :stateId) " +
            "AND (:countryId IS NULL OR c.id = :countryId) " +
            "AND (:status IS NULL OR ci.status = :status)")
    Page<City> search(@Param("search") String search, @Param("stateId") Long stateId,
                       @Param("countryId") Long countryId, @Param("status") CityStatus status, Pageable pageable);
}
