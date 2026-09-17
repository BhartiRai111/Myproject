package com.storehub.controller;

import com.storehub.dto.InventoryResponse;
import com.storehub.dto.InventorySummaryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockAdjustmentRequest;
import com.storehub.dto.StockHistoryResponse;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockStatus;
import com.storehub.security.UserPrincipal;
import com.storehub.service.InventoryService;
import com.storehub.service.StoreAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Every list/summary/export/history endpoint here accepts an optional {@code storeId}
 * filter, resolved through {@link StoreAccessService} rather than trusted directly from
 * the request (Multi-Store spec section 14): an explicit storeId is validated against the
 * caller's own store access (403 if not accessible); with none given, an ALL_STORES caller
 * sees the aggregate across every store while a store-scoped caller is narrowed to their
 * own accessible stores.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final StoreAccessService storeAccessService;

    @GetMapping
    public ResponseEntity<PagedResponse<InventoryResponse>> getInventory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Long resolvedStoreId = storeAccessService.resolveViewableStoreId(principal.getUser(), storeId);
        return ResponseEntity.ok(inventoryService.searchInventory(search, categoryId, resolvedStoreId, stockStatus, page, size, sortBy, sortDir));
    }

    @GetMapping("/summary")
    public ResponseEntity<InventorySummaryResponse> getSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(inventoryService.getSummary(storeAccessService.resolveViewableStoreId(principal.getUser(), storeId)));
    }

    @GetMapping("/history")
    public ResponseEntity<PagedResponse<StockHistoryResponse>> getAllHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long resolvedStoreId = storeAccessService.resolveViewableStoreId(principal.getUser(), storeId);
        return ResponseEntity.ok(inventoryService.searchHistory(productId, resolvedStoreId, movementType, referenceType, fromDate, toDate, page, size));
    }

    /** A specific inventory row belongs to exactly one store — never returned to a caller without access to it (spec section 14). */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getInventoryById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        InventoryResponse response = inventoryService.getInventoryById(id);
        storeAccessService.assertStoreAccess(principal.getUser(), response.getStoreId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<PagedResponse<StockHistoryResponse>> getHistoryForInventory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        storeAccessService.assertStoreAccess(principal.getUser(), inventoryService.getInventoryById(id).getStoreId());
        return ResponseEntity.ok(inventoryService.getHistoryForInventory(id, page, size));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_ADJUST')")
    public ResponseEntity<InventoryResponse> adjustStock(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody StockAdjustmentRequest request) {
        storeAccessService.assertStoreAccess(principal.getUser(), request.getStoreId());
        return ResponseEntity.ok(inventoryService.adjustStock(request));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_ADJUST')")
    public ResponseEntity<byte[]> exportInventory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) StockStatus stockStatus) {
        Long resolvedStoreId = storeAccessService.resolveViewableStoreId(principal.getUser(), storeId);
        String csv = inventoryService.exportCsv(search, categoryId, resolvedStoreId, stockStatus);
        String filename = "inventory-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
