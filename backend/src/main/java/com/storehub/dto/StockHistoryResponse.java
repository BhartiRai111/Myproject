package com.storehub.dto;

import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockHistory;
import com.storehub.entity.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class StockHistoryResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private Long storeId;
    private String storeName;
    private String storeCode;
    private StockMovementType movementType;
    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private String reason;
    private ReferenceType referenceType;
    private Long referenceId;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;

    public static StockHistoryResponse fromEntity(StockHistory history) {
        return StockHistoryResponse.builder()
                .id(history.getId())
                .productId(history.getProduct().getId())
                .productName(history.getProduct().getName())
                .sku(history.getProduct().getSku())
                .storeId(history.getStore() != null ? history.getStore().getId() : null)
                .storeName(history.getStore() != null ? history.getStore().getStoreName() : null)
                .storeCode(history.getStore() != null ? history.getStore().getStoreCode() : null)
                .movementType(history.getMovementType())
                .quantity(history.getQuantity())
                .previousStock(history.getPreviousStock())
                .newStock(history.getNewStock())
                .reason(history.getReason())
                .referenceType(history.getReferenceType())
                .referenceId(history.getReferenceId())
                .notes(history.getNotes())
                .createdAt(history.getCreatedAt())
                .createdBy(history.getCreatedBy())
                .build();
    }
}
