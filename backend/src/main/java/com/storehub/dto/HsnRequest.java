package com.storehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class HsnRequest {

    @NotBlank(message = "HSN code is required")
    private String hsnCode;

    private String description;

    @Valid
    private List<HsnTaxRateRequest> taxRates = new ArrayList<>();
}
