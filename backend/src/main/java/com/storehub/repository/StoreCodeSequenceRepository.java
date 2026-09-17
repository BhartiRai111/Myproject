package com.storehub.repository;

import com.storehub.entity.StoreCodeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreCodeSequenceRepository extends JpaRepository<StoreCodeSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StoreCodeSequence s WHERE s.id = :id")
    Optional<StoreCodeSequence> lockForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = "INSERT INTO store_code_sequence (id, last_number) VALUES (:id, 0) " +
            "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void ensureRowExists(@Param("id") Long id);
}
