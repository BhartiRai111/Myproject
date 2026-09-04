package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CountryRequest {

    @NotBlank(message = "Country name is required")
    private String name;

    @NotBlank(message = "Country code is required")
    private String code;
}
