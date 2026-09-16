package com.storehub.repository;

import com.storehub.entity.ItemSkuSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemSkuSequenceRepository extends JpaRepository<ItemSkuSequence, Long> {

    /** Row-locks the single counter row for the duration of the caller's transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ItemSkuSequence s WHERE s.id = :id")
    Optional<ItemSkuSequence> lockForUpdate(@Param("id") Long id);

    /**
     * Idempotent upsert-if-absent, native so it never throws a constraint-violation exception under
     * a race (which would otherwise mark the JPA transaction rollback-only). Always followed by
     * {@link #lockForUpdate} in the same transaction to actually read/increment the row.
     */
    @Modifying
    @Query(value = "INSERT INTO item_sku_sequence (id, last_number) VALUES (:id, 0) " +
            "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void ensureRowExists(@Param("id") Long id);
}
