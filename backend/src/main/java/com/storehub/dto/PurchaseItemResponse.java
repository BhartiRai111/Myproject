package com.storehub.dto;

import com.storehub.entity.PurchaseItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseItemResponse {

    private Long id;
    private ProductResponse product;
    private Integer quantity;
    private BigDecimal purchasePrice;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal subtotal;

    public static PurchaseItemResponse fromEntity(PurchaseItem item) {
        return PurchaseItemResponse.builder()
                .id(item.getId())
                // Current stock isn't shown for a historical line item, so 0 is a safe placeholder here.
                .product(ProductResponse.fromEntity(item.getProduct(), 0))
                .quantity(item.getQuantity())
                .purchasePrice(item.getPurchasePrice())
                .discount(item.getDiscount())
                .tax(item.getTax())
                .subtotal(item.getSubtotal())
                .build();
    }
}
