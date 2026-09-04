package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CityRequest {

    @NotBlank(message = "City name is required")
    private String name;

    @NotNull(message = "State is required")
    private Long stateId;
}
