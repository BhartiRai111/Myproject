package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessGstConfigRequest {

    @NotBlank(message = "Legal name is required")
    @Size(max = 200, message = "Legal name cannot exceed 200 characters")
    private String legalName;

    @Size(max = 200, message = "Trade name cannot exceed 200 characters")
    private String tradeName;

    private String gstin;

    @Size(max = 10, message = "PAN cannot exceed 10 characters")
    private String pan;

    private String address;

    private Long stateId;

    @Size(max = 10, message = "Pincode cannot exceed 10 characters")
    private String pincode;
}
