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
public class ReceiptRequest {

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Receipt date is required")
    private LocalDate receiptDate;

    @NotNull(message = "Receipt amount is required")
    @DecimalMin(value = "0.01", message = "Receipt amount must be greater than 0")
    private java.math.BigDecimal amount;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    private String remarks;

    /** Optional explicit per-bill allocation; if omitted, allocate FIFO across the customer's outstanding bills. */
    @Valid
    private List<ReceiptAllocationRequest> allocations = new ArrayList<>();
}
