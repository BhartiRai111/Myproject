package com.storehub.controller;

import com.storehub.dto.CurrencyRequest;
import com.storehub.dto.CurrencyResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.CurrencyStatus;
import com.storehub.service.CurrencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<PagedResponse<CurrencyResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CurrencyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(currencyService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurrencyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(currencyService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CurrencyResponse> create(@Valid @RequestBody CurrencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(currencyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CurrencyResponse> update(@PathVariable Long id, @Valid @RequestBody CurrencyRequest request) {
        return ResponseEntity.ok(currencyService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CurrencyResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(currencyService.setStatus(id, CurrencyStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CurrencyResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(currencyService.setStatus(id, CurrencyStatus.INACTIVE));
    }
}
