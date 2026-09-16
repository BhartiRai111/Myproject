package com.storehub.controller;

import com.storehub.dto.AdminPasswordResetRequest;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.PasswordChangeRequest;
import com.storehub.dto.UserCreateRequest;
import com.storehub.dto.UserResponse;
import com.storehub.dto.UserStatusUpdateRequest;
import com.storehub.dto.UserUpdateRequest;
import com.storehub.entity.Permission;
import com.storehub.entity.Role;
import com.storehub.entity.UserStatus;
import com.storehub.security.UserPrincipal;
import com.storehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getUsers(search, role, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /** Troubleshooting/access review (spec section 39): what this user can actually do, without an admin computing it by hand. */
    @GetMapping("/{id}/effective-permissions")
    public ResponseEntity<Set<Permission>> getEffectivePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(userService.effectivePermissions(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        return ResponseEntity.ok(userService.updateStatus(id, request));
    }

    /** ADMIN resets another user's password — never requires or reveals the existing one. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<UserResponse> resetPassword(@PathVariable Long id, @Valid @RequestBody AdminPasswordResetRequest request) {
        return ResponseEntity.ok(userService.adminResetPassword(id, request));
    }

    /**
     * Self-service password change — any authenticated user, for their own account only.
     * Overrides the class-level {@code hasRole('ADMIN')}: method-level {@code @PreAuthorize}
     * takes precedence, so this is reachable by every role, not just ADMIN.
     */
    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody PasswordChangeRequest request) {
        userService.changeOwnPassword(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}
