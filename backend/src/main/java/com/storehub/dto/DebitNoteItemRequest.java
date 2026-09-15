package com.storehub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** See {@link CreditNoteItemRequest} — same "server derives rate/tax" rule, for the Purchase side. */
@Getter
@Setter
public class DebitNoteItemRequest {

    @NotNull(message = "Purchase item is required")
    private Long purchaseItemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;
}
