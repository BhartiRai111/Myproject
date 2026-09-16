package com.storehub.dto;

import com.storehub.entity.PaymentMode;
import com.storehub.entity.TaxMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edits a DRAFT expense in place — same shape as {@link ExpenseCreateRequest}; only a DRAFT can be edited. */
@Getter
@Setter
public class ExpenseUpdateRequest {

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    private Long categoryId;

    private String category;

    private Long supplierId;

    private String vendorName;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    private TaxMode taxMode;

    @DecimalMin(value = "0", message = "GST percent must be greater than or equal to 0")
    private BigDecimal gstPercent;

    private boolean itcEligible;

    private BigDecimal grossAmount;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountAmount;

    private BigDecimal taxableAmount;

    private String description;

    private String referenceNumber;

    private String remarks;

    private boolean post;
}
