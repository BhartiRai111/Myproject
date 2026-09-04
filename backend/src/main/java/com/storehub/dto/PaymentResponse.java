package com.storehub.dto;

import com.storehub.entity.Payment;
import com.storehub.entity.PaymentMode;
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
public class PaymentResponse {

    private Long id;
    private String paymentNumber;
    private LocalDate paymentDate;
    private SupplierResponse supplier;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private String remarks;
    private List<PaymentAllocationResponse> allocations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .paymentDate(payment.getPaymentDate())
                .supplier(payment.getSupplier() != null ? SupplierResponse.fromEntity(payment.getSupplier()) : null)
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
                .remarks(payment.getRemarks())
                .allocations(payment.getAllocations().stream().map(PaymentAllocationResponse::fromEntity).toList())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
