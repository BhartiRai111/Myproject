package com.storehub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Deliberately carries only saleItemId + quantity — rate/discount/GST are always derived
 * server-side from the source SaleItem's own values (Phase 5 spec section 10: "backend
 * validation is the source of truth"), so a client can never claim a different price or
 * tax rate on a return than what was actually billed.
 */
@Getter
@Setter
public class CreditNoteItemRequest {

    @NotNull(message = "Sale item is required")
    private Long saleItemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;
}
