package com.storehub.dto;

import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.PaymentStatus;
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
    private Long categoryId;
    private Long storeId;
    private String storeName;
    private String storeCode;
    private String vendorName;
    private Long supplierId;
    private String supplierGstin;
    private PaymentMode paymentMode;
    private TaxMode taxMode;
    private BigDecimal gstPercent;
    private boolean itcEligible;
    private BigDecimal discountAmount;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal payableAmount;
    private PaymentStatus paymentStatus;
    private String description;
    private String referenceNumber;
    private String remarks;
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
                .categoryId(e.getExpenseCategory() != null ? e.getExpenseCategory().getId() : null)
                .storeId(e.getStore() != null ? e.getStore().getId() : null)
                .storeName(e.getStore() != null ? e.getStore().getStoreName() : null)
                .storeCode(e.getStore() != null ? e.getStore().getStoreCode() : null)
                .vendorName(e.getVendorName())
                .supplierId(e.getSupplier() != null ? e.getSupplier().getId() : null)
                .supplierGstin(e.getSupplier() != null ? e.getSupplier().getGstNumber() : null)
                .paymentMode(e.getPaymentMode())
                .taxMode(e.getTaxMode())
                .gstPercent(e.getGstPercent())
                .itcEligible(e.isItcEligible())
                .discountAmount(e.getDiscountAmount())
                .taxableAmount(e.getTaxableAmount())
                .cgstAmount(e.getCgstAmount())
                .sgstAmount(e.getSgstAmount())
                .igstAmount(e.getIgstAmount())
                .totalAmount(e.getTotalAmount())
                .paidAmount(e.getPaidAmount())
                .payableAmount(e.getPayableAmount())
                .paymentStatus(e.getPaymentStatus())
                .description(e.getDescription())
                .referenceNumber(e.getReferenceNumber())
                .remarks(e.getRemarks())
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
