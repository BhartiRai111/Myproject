package com.storehub.dto;

import com.storehub.entity.CashTransaction;
import com.storehub.entity.CashTransactionStatus;
import com.storehub.entity.CashTransactionType;
import com.storehub.entity.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CashTransactionResponse {

    private Long id;
    private String transactionNumber;
    private LocalDate transactionDate;
    private CashTransactionType transactionType;
    private PaymentMode paymentMode;
    private BigDecimal amount;
    private String reason;
    private CashTransactionStatus status;
    private String createdBy;
    private String postedBy;
    private LocalDateTime postedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;

    public static CashTransactionResponse fromEntity(CashTransaction c) {
        return CashTransactionResponse.builder()
                .id(c.getId())
                .transactionNumber(c.getTransactionNumber())
                .transactionDate(c.getTransactionDate())
                .transactionType(c.getTransactionType())
                .paymentMode(c.getPaymentMode())
                .amount(c.getAmount())
                .reason(c.getReason())
                .status(c.getStatus())
                .createdBy(c.getCreatedBy())
                .postedBy(c.getPostedBy())
                .postedAt(c.getPostedAt())
                .cancelledBy(c.getCancelledBy())
                .cancelledAt(c.getCancelledAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
