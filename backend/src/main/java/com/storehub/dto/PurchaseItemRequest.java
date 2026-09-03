package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseItemRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0", message = "Purchase price must be greater than or equal to 0")
    private BigDecimal purchasePrice;

    @NotNull(message = "Discount is required")
    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull(message = "Tax is required")
    @DecimalMin(value = "0", message = "Tax cannot be negative")
    private BigDecimal tax = BigDecimal.ZERO;
}
