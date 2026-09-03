package com.storehub.dto;

import com.storehub.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PurchaseCreateRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Purchase date is required")
    private LocalDate purchaseDate;

    @NotNull(message = "Payment status is required")
    private PaymentStatus paymentStatus;

    private String notes;

    @NotEmpty(message = "At least one purchase item is required")
    @Valid
    private List<PurchaseItemRequest> items;
}
