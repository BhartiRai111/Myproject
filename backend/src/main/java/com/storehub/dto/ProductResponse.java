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
    private String manualCode;
    private Long itemGroupId;
    private String itemGroupName;
    private Long hsnId;
    private String hsnCode;
    private Long purchaseUnitId;
    private String purchaseUnitName;
    private Long saleUnitId;
    private String saleUnitName;
    private BigDecimal tolerancePercent;
    private String itemType;
    private String taxNature;
    private String taxBasedOn;
    private String partyName;
    private String partyProductName;
    private BigDecimal freeValue;
    private String applicableProperty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product, int currentStock) {
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
                .stockQuantity(currentStock)
                .status(product.getStatus())
                .description(product.getDescription())
                .manualCode(product.getManualCode())
                .itemGroupId(product.getItemGroup() != null ? product.getItemGroup().getId() : null)
                .itemGroupName(product.getItemGroup() != null ? product.getItemGroup().getName() : null)
                .hsnId(product.getHsn() != null ? product.getHsn().getId() : null)
                .hsnCode(product.getHsn() != null ? product.getHsn().getHsnCode() : null)
                .purchaseUnitId(product.getPurchaseUnit() != null ? product.getPurchaseUnit().getId() : null)
                .purchaseUnitName(product.getPurchaseUnit() != null ? product.getPurchaseUnit().getName() : null)
                .saleUnitId(product.getSaleUnit() != null ? product.getSaleUnit().getId() : null)
                .saleUnitName(product.getSaleUnit() != null ? product.getSaleUnit().getName() : null)
                .tolerancePercent(product.getTolerancePercent())
                .itemType(product.getItemType())
                .taxNature(product.getTaxNature())
                .taxBasedOn(product.getTaxBasedOn())
                .partyName(product.getPartyName())
                .partyProductName(product.getPartyProductName())
                .freeValue(product.getFreeValue())
                .applicableProperty(product.getApplicableProperty())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
