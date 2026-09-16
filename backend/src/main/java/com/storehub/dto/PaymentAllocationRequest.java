package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentAllocationRequest {

    /** Exactly one of purchaseId/expenseId must be set. */
    private Long purchaseId;

    /** A credit (party) Expense this payment settles — reuses this same allocation mechanism as a Purchase Bill. */
    private Long expenseId;

    @NotNull(message = "Amount applied is required")
    @DecimalMin(value = "0.01", message = "Amount applied must be greater than 0")
    private BigDecimal amountApplied;
}
