package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentAllocationRequest {

    @NotNull(message = "Purchase bill is required")
    private Long purchaseId;

    @NotNull(message = "Amount applied is required")
    @DecimalMin(value = "0.01", message = "Amount applied must be greater than 0")
    private BigDecimal amountApplied;
}
