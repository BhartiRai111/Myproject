package com.storehub.dto;

import com.storehub.entity.StockMovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockAdjustmentRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Adjustment type is required")
    private StockMovementType movementType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotBlank(message = "Reason is required")
    private String reason;

    private String notes;
}
