package com.storehub.service;

import com.storehub.entity.Permission;
import com.storehub.entity.Role;
import com.storehub.entity.User;
import com.storehub.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * The one centralized authorization-check service (StoreHub Employee/User Roles spec
 * section 40) — {@code @PreAuthorize("hasAuthority('PERM_...')")} is the primary
 * enforcement mechanism (backed by the same {@link RolePermissions} map), but a handful
 * of checks (admin-safety, the effective-permissions endpoint, self-service guards) need
 * a plain Java call rather than a SpEL expression; this is the one place those go, so
 * they read from the identical source of truth instead of re-deriving it.
 */
@Service
public class PermissionService {

    public boolean hasPermission(User user, Permission permission) {
        return user != null && RolePermissions.has(user.getRole(), permission);
    }

    public boolean currentUserHasPermission(Permission permission) {
        return hasPermission(SecurityUtil.currentUserOrNull(), permission);
    }

    public Set<Permission> effectivePermissions(Role role) {
        return RolePermissions.forRole(role);
    }
}
