package com.storehub.repository;

import com.storehub.entity.State;
import com.storehub.entity.StateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StateRepository extends JpaRepository<State, Long> {

    boolean existsByNameIgnoreCaseAndCountryId(String name, Long countryId);

    boolean existsByNameIgnoreCaseAndCountryIdAndIdNot(String name, Long countryId, Long id);

    @Query("SELECT s FROM State s JOIN s.country c WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:countryId IS NULL OR c.id = :countryId) " +
            "AND (:status IS NULL OR s.status = :status)")
    Page<State> search(@Param("search") String search, @Param("countryId") Long countryId,
                        @Param("status") StateStatus status, Pageable pageable);
}
