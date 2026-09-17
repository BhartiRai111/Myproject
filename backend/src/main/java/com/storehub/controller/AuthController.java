package com.storehub.controller;

import com.storehub.dto.AuthResponse;
import com.storehub.dto.LoginRequest;
import com.storehub.dto.RegisterRequest;
import com.storehub.dto.SetCurrentStoreRequest;
import com.storehub.dto.StoreResponse;
import com.storehub.dto.UserResponse;
import com.storehub.security.UserPrincipal;
import com.storehub.service.AuthService;
import com.storehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getCurrentUser(principal.getUsername()));
    }

    /** Every store the caller may act on — all stores for an ALL_STORES user, else their explicit assignments (Multi-Store spec section 55). */
    @GetMapping("/my-stores")
    public ResponseEntity<List<StoreResponse>> getMyStores(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getAccessibleStores(principal.getUsername()));
    }

    /** Self-service current-store switch (Multi-Store spec sections 12, 45) — backend-validated against the caller's own store access. */
    @PutMapping("/current-store")
    public ResponseEntity<UserResponse> setCurrentStore(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody SetCurrentStoreRequest request) {
        return ResponseEntity.ok(userService.setCurrentStore(principal.getUsername(), request.getStoreId()));
    }
}
