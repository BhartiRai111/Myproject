package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.StateRequest;
import com.storehub.dto.StateResponse;
import com.storehub.entity.StateStatus;
import com.storehub.service.StateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/states")
@RequiredArgsConstructor
public class StateController {

    private final StateService stateService;

    @GetMapping
    public ResponseEntity<PagedResponse<StateResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) StateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(stateService.search(search, countryId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StateResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(stateService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<StateResponse> create(@Valid @RequestBody StateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stateService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<StateResponse> update(@PathVariable Long id, @Valid @RequestBody StateRequest request) {
        return ResponseEntity.ok(stateService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<StateResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(stateService.setStatus(id, StateStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<StateResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(stateService.setStatus(id, StateStatus.INACTIVE));
    }
}
