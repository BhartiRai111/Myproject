package com.storehub.dto;

import com.storehub.entity.PurchaseOrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseOrderItemResponse {

    private Long id;
    private ProductResponse product;
    private Integer quantity;
    private BigDecimal rate;
    private BigDecimal discount;
    private BigDecimal gstPercent;
    private BigDecimal taxableAmount;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private Integer receivedQuantity;
    private Integer remainingQuantity;

    public static PurchaseOrderItemResponse fromEntity(PurchaseOrderItem item) {
        return PurchaseOrderItemResponse.builder()
                .id(item.getId())
                .product(ProductResponse.fromEntity(item.getProduct(), 0))
                .quantity(item.getQuantity())
                .rate(item.getRate())
                .discount(item.getDiscount())
                .gstPercent(item.getGstPercent())
                .taxableAmount(item.getTaxableAmount())
                .gstAmount(item.getGstAmount())
                .totalAmount(item.getTotalAmount())
                .receivedQuantity(item.getReceivedQuantity())
                .remainingQuantity(item.getRemainingQuantity())
                .build();
    }
}
