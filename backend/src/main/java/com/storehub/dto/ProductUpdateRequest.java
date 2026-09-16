package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductUpdateRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9\\-_/]+$", message = "SKU can only contain letters, numbers, hyphens, underscores, and slashes")
    private String sku;

    private String barcode;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private String brand;

    private String unit;

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0", message = "Purchase price must be greater than or equal to 0")
    private BigDecimal purchasePrice;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0", message = "Selling price must be greater than or equal to 0")
    private BigDecimal sellingPrice;

    @DecimalMin(value = "0", message = "Tax must be greater than or equal to 0")
    private BigDecimal tax;

    @Min(value = 0, message = "Minimum stock level cannot be negative")
    private Integer minStockLevel;

    @Min(value = 0, message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    @Min(value = 0, message = "Reorder quantity cannot be negative")
    private Integer reorderQuantity;

    @Min(value = 0, message = "Maximum stock level cannot be negative")
    private Integer maxStockLevel;

    @DecimalMin(value = "0", message = "MRP must be greater than or equal to 0")
    private BigDecimal mrp;

    @DecimalMin(value = "0", message = "Wholesale price must be greater than or equal to 0")
    private BigDecimal wholesalePrice;

    private String description;

    private String manualCode;

    private Long itemGroupId;

    private Long hsnId;

    private Long purchaseUnitId;

    private Long saleUnitId;

    @DecimalMin(value = "0", message = "Tolerance percent must be greater than or equal to 0")
    private BigDecimal tolerancePercent;

    private String itemType;

    private String taxNature;

    private String taxBasedOn;

    private String partyName;

    private String partyProductName;

    @DecimalMin(value = "0", message = "Free value must be greater than or equal to 0")
    private BigDecimal freeValue;

    private String applicableProperty;
}
