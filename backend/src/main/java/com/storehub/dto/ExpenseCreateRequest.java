package com.storehub.dto;

import com.storehub.entity.PaymentMode;
import com.storehub.entity.TaxMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseCreateRequest {

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @NotBlank(message = "Category is required")
    private String category;

    private String vendorName;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    private TaxMode taxMode;

    @DecimalMin(value = "0", message = "GST percent must be greater than or equal to 0")
    private BigDecimal gstPercent;

    private boolean itcEligible;

    @NotNull(message = "Taxable amount is required")
    @DecimalMin(value = "0.01", message = "Taxable amount must be greater than 0")
    private BigDecimal taxableAmount;

    private String description;

    private boolean post;
}
