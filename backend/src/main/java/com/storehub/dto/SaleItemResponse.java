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
    private BigDecimal gstPercent;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private Long salesOrderItemId;

    public static SaleItemResponse fromEntity(SaleItem item) {
        return SaleItemResponse.builder()
                .id(item.getId())
                // Current stock isn't shown for a historical line item, so 0 is a safe placeholder here.
                .product(ProductResponse.fromEntity(item.getProduct(), 0))
                .quantity(item.getQuantity())
                .sellingPrice(item.getSellingPrice())
                .discount(item.getDiscount())
                .tax(item.getTax())
                .subtotal(item.getSubtotal())
                .gstPercent(item.getGstPercent())
                .taxableAmount(item.getTaxableAmount())
                .cgstAmount(item.getCgstAmount())
                .sgstAmount(item.getSgstAmount())
                .igstAmount(item.getIgstAmount())
                .salesOrderItemId(item.getSalesOrderItem() != null ? item.getSalesOrderItem().getId() : null)
                .build();
    }
}
