package com.storehub.controller;

import com.storehub.dto.ItemGroupRequest;
import com.storehub.dto.ItemGroupResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.ItemGroupStatus;
import com.storehub.service.ItemGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/item-groups")
@RequiredArgsConstructor
public class ItemGroupController {

    private final ItemGroupService itemGroupService;

    @GetMapping
    public ResponseEntity<PagedResponse<ItemGroupResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ItemGroupStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(itemGroupService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemGroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(itemGroupService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ItemGroupResponse> create(@Valid @RequestBody ItemGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemGroupService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ItemGroupResponse> update(@PathVariable Long id, @Valid @RequestBody ItemGroupRequest request) {
        return ResponseEntity.ok(itemGroupService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ItemGroupResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(itemGroupService.setStatus(id, ItemGroupStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ItemGroupResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(itemGroupService.setStatus(id, ItemGroupStatus.INACTIVE));
    }
}
