package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PartyRequest;
import com.storehub.dto.PartyResponse;
import com.storehub.entity.PartyStatus;
import com.storehub.entity.PartyType;
import com.storehub.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @GetMapping
    public ResponseEntity<PagedResponse<PartyResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PartyType partyType,
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(partyService.search(search, partyType, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(partyService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<PartyResponse> create(@Valid @RequestBody PartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<PartyResponse> update(@PathVariable Long id, @Valid @RequestBody PartyRequest request) {
        return ResponseEntity.ok(partyService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<PartyResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(partyService.setStatus(id, PartyStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<PartyResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(partyService.setStatus(id, PartyStatus.INACTIVE));
    }
}
