package com.storehub.dto;

import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String barcode;
    private Long categoryId;
    private String categoryName;
    private String brand;
    private String unit;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private BigDecimal tax;
    private Integer minStockLevel;
    private Integer stockQuantity;
    private ProductStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brand(product.getBrand())
                .unit(product.getUnit())
                .purchasePrice(product.getPurchasePrice())
                .sellingPrice(product.getSellingPrice())
                .tax(product.getTax())
                .minStockLevel(product.getMinStockLevel())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .description(product.getDescription())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
