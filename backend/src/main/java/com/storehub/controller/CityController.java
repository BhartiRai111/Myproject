package com.storehub.controller;

import com.storehub.dto.CityRequest;
import com.storehub.dto.CityResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.CityStatus;
import com.storehub.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping
    public ResponseEntity<PagedResponse<CityResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long stateId,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) CityStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(cityService.search(search, stateId, countryId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CityResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cityService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CityResponse> update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return ResponseEntity.ok(cityService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CityResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(cityService.setStatus(id, CityStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CityResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(cityService.setStatus(id, CityStatus.INACTIVE));
    }
}
