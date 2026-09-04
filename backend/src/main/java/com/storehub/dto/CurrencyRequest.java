package com.storehub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrencyRequest {

    @NotBlank(message = "Currency name is required")
    private String name;

    @NotBlank(message = "Currency code is required")
    private String code;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Decimal places is required")
    @Min(value = 0, message = "Decimal places cannot be negative")
    @Max(value = 4, message = "Decimal places cannot be more than 4")
    private Integer decimalPlaces;
}
