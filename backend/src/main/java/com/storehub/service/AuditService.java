package com.storehub.service;

import com.storehub.entity.AuditAction;
import com.storehub.entity.AuditLog;
import com.storehub.entity.User;
import com.storehub.repository.AuditLogRepository;
import com.storehub.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The one place every audited action is recorded (Phase 5 spec sections
 * 14-15). Runs in the SAME transaction as the caller (default propagation,
 * deliberately not {@code REQUIRES_NEW} like {@link VoucherNumberService}):
 * an audit entry for an action that later rolls back should roll back with
 * it — recording "this happened" for something that didn't would be worse
 * than not recording it. A logging failure itself is caught and logged
 * rather than thrown, so a transient audit-write problem never blocks the
 * business operation it is merely describing.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(AuditAction action, String module, String entityType, Long entityId,
                     String documentNumber, String oldValue, String newValue, String description) {
        try {
            User user = SecurityUtil.currentUserOrNull();
            auditLogRepository.save(AuditLog.builder()
                    .userId(user != null ? user.getId() : null)
                    .username(user != null ? SecurityUtil.currentUsername() : "System")
                    .action(action)
                    .module(module)
                    .entityType(entityType)
                    .entityId(entityId)
                    .documentNumber(documentNumber)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(currentIpAddress())
                    .userAgent(currentUserAgent())
                    .description(description)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log for action={} entityType={} entityId={}: {}", action, entityType, entityId, e.getMessage());
        }
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
