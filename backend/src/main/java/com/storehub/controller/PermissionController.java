package com.storehub.controller;

import com.storehub.dto.PermissionResponse;
import com.storehub.entity.Permission;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only catalog of every permission the system knows about, grouped by module (spec section 62). */
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasAuthority('PERM_PERMISSION_VIEW')")
public class PermissionController {

    @GetMapping
    public ResponseEntity<Map<String, List<PermissionResponse>>> list() {
        Map<String, List<PermissionResponse>> grouped = Arrays.stream(Permission.values())
                .map(PermissionResponse::fromPermission)
                .collect(Collectors.groupingBy(PermissionResponse::getModule, LinkedHashMap::new, Collectors.toList()));
        return ResponseEntity.ok(grouped);
    }
}
