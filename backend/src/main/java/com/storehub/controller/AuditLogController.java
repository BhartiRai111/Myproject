package com.storehub.controller;

import com.storehub.dto.AuditLogResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AuditAction;
import com.storehub.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** ADMIN-only, read-only — the audit trail is append-only and never editable/deletable via the API (Phase 5 spec section 15). */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<PagedResponse<AuditLogResponse>> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLogResponse> result = auditLogRepository
                .search(action, module, entityType, entityId, userId, search, fromDate, toDate, pageable)
                .map(AuditLogResponse::fromEntity);
        return ResponseEntity.ok(PagedResponse.fromPage(result));
    }
}
