package com.storehub.dto;

import com.storehub.entity.Purchase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class OutstandingPurchaseBillResponse {

    private Long purchaseId;
    private String purchaseNumber;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal payableAmount;

    public static OutstandingPurchaseBillResponse fromEntity(Purchase purchase) {
        return OutstandingPurchaseBillResponse.builder()
                .purchaseId(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .purchaseDate(purchase.getPurchaseDate())
                .totalAmount(purchase.getTotalAmount())
                .paidAmount(purchase.getPaidAmount())
                .payableAmount(purchase.getPayableAmount())
                .build();
    }
}
