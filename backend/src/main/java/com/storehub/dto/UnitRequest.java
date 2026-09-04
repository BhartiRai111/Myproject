package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnitRequest {

    @NotBlank(message = "Unit name is required")
    private String name;

    @NotBlank(message = "Unit symbol is required")
    private String symbol;
}
