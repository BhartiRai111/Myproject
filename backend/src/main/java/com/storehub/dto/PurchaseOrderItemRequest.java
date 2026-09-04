package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseOrderItemRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull(message = "Purchase rate is required")
    @DecimalMin(value = "0", message = "Purchase rate must be greater than or equal to 0")
    private BigDecimal rate;

    @NotNull(message = "Discount is required")
    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull(message = "GST percent is required")
    @DecimalMin(value = "0", message = "GST percent cannot be negative")
    private BigDecimal gstPercent = BigDecimal.ZERO;
}
