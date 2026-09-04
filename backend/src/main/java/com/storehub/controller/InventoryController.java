package com.storehub.controller;

import com.storehub.dto.InventoryResponse;
import com.storehub.dto.InventorySummaryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockAdjustmentRequest;
import com.storehub.dto.StockHistoryResponse;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockStatus;
import com.storehub.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<PagedResponse<InventoryResponse>> getInventory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(inventoryService.searchInventory(search, categoryId, stockStatus, page, size, sortBy, sortDir));
    }

    @GetMapping("/summary")
    public ResponseEntity<InventorySummaryResponse> getSummary() {
        return ResponseEntity.ok(inventoryService.getSummary());
    }

    @GetMapping("/history")
    public ResponseEntity<PagedResponse<StockHistoryResponse>> getAllHistory(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(inventoryService.searchHistory(productId, movementType, referenceType, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getInventoryById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getInventoryById(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<PagedResponse<StockHistoryResponse>> getHistoryForInventory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(inventoryService.getHistoryForInventory(id, page, size));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<InventoryResponse> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(request));
    }
}
