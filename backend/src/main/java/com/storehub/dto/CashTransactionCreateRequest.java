package com.storehub.dto;

import com.storehub.entity.CashTransactionType;
import com.storehub.entity.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CashTransactionCreateRequest {

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    /** Optional — validated against the caller's own store access; falls back to their current store when omitted. */
    private Long storeId;

    @NotNull(message = "Transaction type is required")
    private CashTransactionType transactionType;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    private String reason;

    private boolean post;
}
