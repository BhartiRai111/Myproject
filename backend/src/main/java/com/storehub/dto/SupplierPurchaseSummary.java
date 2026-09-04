package com.storehub.dto;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class SupplierPurchaseSummary {

    private Long id;
    private String purchaseNumber;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private PurchaseStatus status;

    public static SupplierPurchaseSummary fromEntity(Purchase purchase) {
        return SupplierPurchaseSummary.builder()
                .id(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .purchaseDate(purchase.getPurchaseDate())
                .totalAmount(purchase.getTotalAmount())
                .paymentStatus(purchase.getPaymentStatus())
                .status(purchase.getStatus())
                .build();
    }
}
