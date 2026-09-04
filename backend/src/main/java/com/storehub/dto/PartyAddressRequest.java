package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartyAddressRequest {

    @NotBlank(message = "Address label is required")
    private String label;

    @NotBlank(message = "Address line is required")
    private String addressLine;

    private Long cityId;

    private Long stateId;

    private String pincode;
}
