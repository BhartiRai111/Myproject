package com.storehub.controller;

import com.storehub.dto.CountryRequest;
import com.storehub.dto.CountryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.CountryStatus;
import com.storehub.service.CountryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @GetMapping
    public ResponseEntity<PagedResponse<CountryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CountryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(countryService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CountryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(countryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CountryResponse> create(@Valid @RequestBody CountryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(countryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CountryResponse> update(@PathVariable Long id, @Valid @RequestBody CountryRequest request) {
        return ResponseEntity.ok(countryService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CountryResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(countryService.setStatus(id, CountryStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CountryResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(countryService.setStatus(id, CountryStatus.INACTIVE));
    }
}
