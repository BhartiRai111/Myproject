package com.storehub.dto;

import com.storehub.entity.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PaymentRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
    private java.math.BigDecimal amount;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    private String remarks;

    /** Optional explicit per-bill allocation; if omitted, allocate FIFO across the supplier's outstanding bills. */
    @Valid
    private List<PaymentAllocationRequest> allocations = new ArrayList<>();
}
