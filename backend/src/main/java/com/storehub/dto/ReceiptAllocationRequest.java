package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReceiptAllocationRequest {

    @NotNull(message = "Sale is required")
    private Long saleId;

    @NotNull(message = "Amount applied is required")
    @DecimalMin(value = "0.01", message = "Amount applied must be greater than 0")
    private BigDecimal amountApplied;
}
