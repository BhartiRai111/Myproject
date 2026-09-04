package com.storehub.dto;

import com.storehub.entity.PaymentMode;
import com.storehub.entity.Receipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ReceiptResponse {

    private Long id;
    private String receiptNumber;
    private LocalDate receiptDate;
    private CustomerResponse customer;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private String remarks;
    private List<ReceiptAllocationResponse> allocations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReceiptResponse fromEntity(Receipt receipt) {
        return ReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .receiptDate(receipt.getReceiptDate())
                .customer(receipt.getCustomer() != null ? CustomerResponse.fromEntity(receipt.getCustomer()) : null)
                .amount(receipt.getAmount())
                .paymentMode(receipt.getPaymentMode())
                .remarks(receipt.getRemarks())
                .allocations(receipt.getAllocations().stream().map(ReceiptAllocationResponse::fromEntity).toList())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}
