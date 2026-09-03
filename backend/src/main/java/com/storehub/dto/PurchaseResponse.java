package com.storehub.dto;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
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
public class PurchaseResponse {

    private Long id;
    private String purchaseNumber;
    private SupplierResponse supplier;
    private LocalDate purchaseDate;
    private List<PurchaseItemResponse> items;
    private BigDecimal subtotalAmount;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private PurchaseStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseResponse fromEntity(Purchase purchase) {
        List<PurchaseItemResponse> items = purchase.getItems().stream()
                .map(PurchaseItemResponse::fromEntity)
                .toList();

        BigDecimal subtotalAmount = purchase.getItems().stream()
                .map(item -> item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = purchase.getItems().stream()
                .map(item -> item.getDiscount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = purchase.getItems().stream()
                .map(item -> item.getTax())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PurchaseResponse.builder()
                .id(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .supplier(SupplierResponse.fromEntity(purchase.getSupplier()))
                .purchaseDate(purchase.getPurchaseDate())
                .items(items)
                .subtotalAmount(subtotalAmount)
                .totalDiscount(totalDiscount)
                .totalTax(totalTax)
                .totalAmount(purchase.getTotalAmount())
                .paymentStatus(purchase.getPaymentStatus())
                .status(purchase.getStatus())
                .notes(purchase.getNotes())
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .build();
    }
}
