package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneRequest {

    @NotBlank(message = "Zone name is required")
    private String name;

    private String code;

    private Long countryId;

    private Long stateId;
}
