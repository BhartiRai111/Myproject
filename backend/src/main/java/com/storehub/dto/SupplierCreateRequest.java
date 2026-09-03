package com.storehub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierCreateRequest {

    @NotBlank(message = "Supplier name is required")
    private String name;

    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    private String address;
}
