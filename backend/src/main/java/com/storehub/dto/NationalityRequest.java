package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NationalityRequest {

    @NotBlank(message = "Nationality name is required")
    private String name;

    @NotNull(message = "Country is required")
    private Long countryId;
}
