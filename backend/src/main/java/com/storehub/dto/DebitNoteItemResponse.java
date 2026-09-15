package com.storehub.dto;

import com.storehub.entity.DebitNoteItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class DebitNoteItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long purchaseItemId;
    private Integer quantity;
    private BigDecimal rate;
    private BigDecimal discount;
    private BigDecimal taxableAmount;
    private BigDecimal gstPercent;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal total;

    public static DebitNoteItemResponse fromEntity(DebitNoteItem item) {
        return DebitNoteItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .purchaseItemId(item.getPurchaseItem().getId())
                .quantity(item.getQuantity())
                .rate(item.getRate())
                .discount(item.getDiscount())
                .taxableAmount(item.getTaxableAmount())
                .gstPercent(item.getGstPercent())
                .cgstAmount(item.getCgstAmount())
                .sgstAmount(item.getSgstAmount())
                .igstAmount(item.getIgstAmount())
                .total(item.getTotal())
                .build();
    }
}
