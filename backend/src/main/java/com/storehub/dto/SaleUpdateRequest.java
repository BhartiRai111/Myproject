package com.storehub.dto;

import com.storehub.entity.GstType;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.TaxMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class SaleUpdateRequest {

    private Long customerId;

    @NotNull(message = "Sale date is required")
    private LocalDate saleDate;

    @NotNull(message = "Sale status is required")
    private SaleStatus status;

    @NotNull(message = "Sale type is required")
    private GstType gstType;

    private TaxMode taxMode;

    private String customerPhone;

    private String customerGstin;

    private String billingAddress;

    private String shippingAddress;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    @NotNull(message = "Paid amount is required")
    @DecimalMin(value = "0", message = "Paid amount cannot be negative")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String notes;

    @NotEmpty(message = "At least one sale item is required")
    @Valid
    private List<SaleItemRequest> items;
}
