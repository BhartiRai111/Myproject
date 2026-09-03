package com.storehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductCreateRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String unit;

    @DecimalMin(value = "0", message = "Selling price must be greater than or equal to 0")
    private BigDecimal sellingPrice;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;
}
