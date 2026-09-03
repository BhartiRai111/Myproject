package com.storehub.dto;

import com.storehub.entity.SaleItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SaleItemResponse {

    private Long id;
    private ProductResponse product;
    private Integer quantity;
    private BigDecimal sellingPrice;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal subtotal;

    public static SaleItemResponse fromEntity(SaleItem item) {
        return SaleItemResponse.builder()
                .id(item.getId())
                .product(ProductResponse.fromEntity(item.getProduct()))
                .quantity(item.getQuantity())
                .sellingPrice(item.getSellingPrice())
                .discount(item.getDiscount())
                .tax(item.getTax())
                .subtotal(item.getSubtotal())
                .build();
    }
}
