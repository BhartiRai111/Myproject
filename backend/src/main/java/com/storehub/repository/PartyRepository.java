package com.storehub.repository;

import com.storehub.entity.Party;
import com.storehub.entity.PartyStatus;
import com.storehub.entity.PartyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long> {

    boolean existsByPartyCodeIgnoreCase(String partyCode);

    boolean existsByPartyCodeIgnoreCaseAndIdNot(String partyCode, Long id);

    boolean existsByGstNumberIgnoreCase(String gstNumber);

    boolean existsByGstNumberIgnoreCaseAndIdNot(String gstNumber, Long id);

    @Query("SELECT p FROM Party p WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(p.partyName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.partyCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR p.mobile LIKE CONCAT('%', :search, '%')) " +
            "AND (:partyType IS NULL OR p.partyType = :partyType) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Party> search(@Param("search") String search, @Param("partyType") PartyType partyType,
                        @Param("status") PartyStatus status, Pageable pageable);
}
