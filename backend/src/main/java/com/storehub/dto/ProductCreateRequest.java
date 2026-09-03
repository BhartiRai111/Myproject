package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCreateRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String unit;
}
