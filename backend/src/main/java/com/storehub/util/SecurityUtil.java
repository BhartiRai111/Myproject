package com.storehub.util;

import com.storehub.entity.User;
import com.storehub.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Shared "who is doing this" helper for services that need it for
 * createdBy/postedBy/audit fields (AccountingService, VoucherNumberService,
 * AuditService, note services, ...). Mirrors the logic AccountingService
 * already had privately; centralized here rather than re-duplicated again.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static String currentUsername() {
        User user = currentUserOrNull();
        if (user == null) {
            return "System";
        }
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (user.getFirstName() + " " + lastName).trim();
    }

    public static Long currentUserId() {
        User user = currentUserOrNull();
        return user != null ? user.getId() : null;
    }

    public static User currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUser();
        }
        return null;
    }
}
