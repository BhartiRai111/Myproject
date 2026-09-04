package com.storehub.controller;

import com.storehub.dto.NationalityRequest;
import com.storehub.dto.NationalityResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.NationalityStatus;
import com.storehub.service.NationalityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/nationalities")
@RequiredArgsConstructor
public class NationalityController {

    private final NationalityService nationalityService;

    @GetMapping
    public ResponseEntity<PagedResponse<NationalityResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) NationalityStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(nationalityService.search(search, countryId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NationalityResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(nationalityService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<NationalityResponse> create(@Valid @RequestBody NationalityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nationalityService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<NationalityResponse> update(@PathVariable Long id, @Valid @RequestBody NationalityRequest request) {
        return ResponseEntity.ok(nationalityService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<NationalityResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(nationalityService.setStatus(id, NationalityStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<NationalityResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(nationalityService.setStatus(id, NationalityStatus.INACTIVE));
    }
}
