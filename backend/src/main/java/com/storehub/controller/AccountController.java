package com.storehub.controller;

import com.storehub.dto.AccountGroupResponse;
import com.storehub.dto.AccountRequest;
import com.storehub.dto.AccountResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AccountType;
import com.storehub.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<PagedResponse<AccountResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountService.search(search, accountType, active, page, size));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<AccountGroupResponse>> listGroups() {
        return ResponseEntity.ok(accountService.listGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.update(id, request));
    }
}
