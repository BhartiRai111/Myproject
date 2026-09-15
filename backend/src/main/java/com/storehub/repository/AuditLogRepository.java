package com.storehub.repository;

import com.storehub.entity.AuditAction;
import com.storehub.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** No update/delete methods — see {@link AuditLog}'s Javadoc: this trail is append-only by design. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:action IS NULL OR a.action = :action) " +
            "AND (:module IS NULL OR :module = '' OR a.module = :module) " +
            "AND (:entityType IS NULL OR :entityType = '' OR a.entityType = :entityType) " +
            "AND (:entityId IS NULL OR a.entityId = :entityId) " +
            "AND (:userId IS NULL OR a.userId = :userId) " +
            "AND (:search IS NULL OR :search = '' OR " +
            "  LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(a.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:fromDate IS NULL OR a.timestamp >= :fromDate) " +
            "AND (:toDate IS NULL OR a.timestamp <= :toDate)")
    Page<AuditLog> search(@Param("action") AuditAction action,
                           @Param("module") String module,
                           @Param("entityType") String entityType,
                           @Param("entityId") Long entityId,
                           @Param("userId") Long userId,
                           @Param("search") String search,
                           @Param("fromDate") LocalDateTime fromDate,
                           @Param("toDate") LocalDateTime toDate,
                           Pageable pageable);
}
