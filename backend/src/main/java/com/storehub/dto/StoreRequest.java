package com.storehub.dto;

import com.storehub.entity.StoreType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreRequest {

    @NotBlank(message = "Store code is required")
    private String storeCode;

    @NotBlank(message = "Store name is required")
    private String storeName;

    @NotNull(message = "Store type is required")
    private StoreType storeType;

    private String legalName;

    private String address;

    private Long countryId;

    private Long stateId;

    private Long cityId;

    private Long zoneId;

    private String pincode;

    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    /** Optional — must be a valid 15-character GSTIN when supplied. */
    @Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}Z[0-9A-Za-z]{1}$", message = "GSTIN format is invalid")
    private String gstin;
}
