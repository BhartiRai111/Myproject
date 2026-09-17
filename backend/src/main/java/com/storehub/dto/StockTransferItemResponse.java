package com.storehub.dto;

import com.storehub.entity.StockTransferItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StockTransferItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private Integer quantity;
    private String notes;

    public static StockTransferItemResponse fromEntity(StockTransferItem item) {
        return StockTransferItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .sku(item.getProduct().getSku())
                .quantity(item.getQuantity())
                .notes(item.getNotes())
                .build();
    }
}
