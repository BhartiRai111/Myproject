package com.storehub.controller;

import com.storehub.dto.PermissionResponse;
import com.storehub.dto.RoleResponse;
import com.storehub.entity.Role;
import com.storehub.service.RolePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only role/permission browsing for the Roles &amp; Permissions admin page (spec
 * sections 60-61). Roles are a fixed code enum here (see {@link Role}), not a DB table,
 * so there is nothing to create/rename/delete — only {@code RolePermissions} (the
 * ADMIN-editable mapping) is out of scope for this step's read API.
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('PERM_ROLE_VIEW')")
public class RoleController {

    @GetMapping
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(Arrays.stream(Role.values())
                .map(RoleResponse::fromRole)
                .collect(Collectors.toList()));
    }

    /** Role detail (spec section 61): assigned permissions grouped by module. */
    @GetMapping("/{role}/permissions")
    public ResponseEntity<Map<String, List<PermissionResponse>>> permissionsFor(@PathVariable Role role) {
        Map<String, List<PermissionResponse>> grouped = RolePermissions.forRole(role).stream()
                .map(PermissionResponse::fromPermission)
                .collect(Collectors.groupingBy(PermissionResponse::getModule, LinkedHashMap::new, Collectors.toList()));
        return ResponseEntity.ok(grouped);
    }
}
