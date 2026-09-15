package com.storehub.dto;

import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.TaxMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String expenseNumber;
    private LocalDate expenseDate;
    private String category;
    private String vendorName;
    private PaymentMode paymentMode;
    private TaxMode taxMode;
    private BigDecimal gstPercent;
    private boolean itcEligible;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmount;
    private String description;
    private ExpenseStatus status;
    private String createdBy;
    private String postedBy;
    private LocalDateTime postedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseResponse fromEntity(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .expenseNumber(e.getExpenseNumber())
                .expenseDate(e.getExpenseDate())
                .category(e.getCategory())
                .vendorName(e.getVendorName())
                .paymentMode(e.getPaymentMode())
                .taxMode(e.getTaxMode())
                .gstPercent(e.getGstPercent())
                .itcEligible(e.isItcEligible())
                .taxableAmount(e.getTaxableAmount())
                .cgstAmount(e.getCgstAmount())
                .sgstAmount(e.getSgstAmount())
                .igstAmount(e.getIgstAmount())
                .totalAmount(e.getTotalAmount())
                .description(e.getDescription())
                .status(e.getStatus())
                .createdBy(e.getCreatedBy())
                .postedBy(e.getPostedBy())
                .postedAt(e.getPostedAt())
                .cancelledBy(e.getCancelledBy())
                .cancelledAt(e.getCancelledAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
