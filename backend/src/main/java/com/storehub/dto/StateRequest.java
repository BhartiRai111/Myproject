package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StateRequest {

    @NotBlank(message = "State name is required")
    private String name;

    private String code;

    @NotNull(message = "Country is required")
    private Long countryId;
}
