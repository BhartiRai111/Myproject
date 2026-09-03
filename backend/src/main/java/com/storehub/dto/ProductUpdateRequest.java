package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductUpdateRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "SKU is required")
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

    private String description;
}
