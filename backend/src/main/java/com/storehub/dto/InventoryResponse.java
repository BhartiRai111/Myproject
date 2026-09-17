package com.storehub.dto;

import com.storehub.entity.Inventory;
import com.storehub.entity.Product;
import com.storehub.entity.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class InventoryResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private Long categoryId;
    private String categoryName;
    private Long storeId;
    private String storeName;
    private String storeCode;
    private String unit;
    private Integer currentStock;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private StockStatus stockStatus;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventoryResponse fromEntity(Inventory inventory) {
        Product product = inventory.getProduct();
        int minStockLevel = product.getMinStockLevel() != null ? product.getMinStockLevel() : 0;
        int currentStock = inventory.getCurrentStock();

        Integer maxStockLevel = product.getMaxStockLevel();

        StockStatus stockStatus;
        if (currentStock <= 0) {
            stockStatus = StockStatus.OUT_OF_STOCK;
        } else if (currentStock <= minStockLevel) {
            stockStatus = StockStatus.LOW_STOCK;
        } else if (maxStockLevel != null && currentStock > maxStockLevel) {
            stockStatus = StockStatus.OVERSTOCK;
        } else {
            stockStatus = StockStatus.IN_STOCK;
        }

        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .storeId(inventory.getStore() != null ? inventory.getStore().getId() : null)
                .storeName(inventory.getStore() != null ? inventory.getStore().getStoreName() : null)
                .storeCode(inventory.getStore() != null ? inventory.getStore().getStoreCode() : null)
                .unit(product.getUnit())
                .currentStock(currentStock)
                .minStockLevel(minStockLevel)
                .maxStockLevel(maxStockLevel)
                .reorderLevel(product.getReorderLevel())
                .reorderQuantity(product.getReorderQuantity())
                .stockStatus(stockStatus)
                .lastUpdated(inventory.getUpdatedAt())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
