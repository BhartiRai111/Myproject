package com.storehub.dto;

import com.storehub.entity.PaymentMode;
import com.storehub.entity.TaxMode;
import jakarta.validation.constraints.DecimalMin;
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

    /** Optional — validated against the caller's own store access; falls back to their current store when omitted. */
    private Long storeId;

    /** Preferred: a master ExpenseCategory id. When set, its name is snapshotted into the legacy {@code category} string. */
    private Long categoryId;

    /** Legacy free-text category name; still accepted when categoryId is not sent (kept for API/back-compat). */
    private String category;

    /** Optional party (Supplier) for a credit expense — when set, the expense books to Supplier Payable instead of Cash/Bank. */
    private Long supplierId;

    private String vendorName;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    private TaxMode taxMode;

    @DecimalMin(value = "0", message = "GST percent must be greater than or equal to 0")
    private BigDecimal gstPercent;

    private boolean itcEligible;

    /** Optional: when sent together with discountAmount, taxableAmount is computed server-side as grossAmount - discountAmount. */
    private BigDecimal grossAmount;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountAmount;

    /** Required only when grossAmount is not sent; otherwise computed server-side and this value is ignored. */
    private BigDecimal taxableAmount;

    private String description;

    private String referenceNumber;

    private String remarks;

    private boolean post;
}
