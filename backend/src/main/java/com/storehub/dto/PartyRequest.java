package com.storehub.dto;

import com.storehub.entity.PartyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class PartyRequest {

    @NotBlank(message = "Party code is required")
    private String partyCode;

    @NotBlank(message = "Party name is required")
    private String partyName;

    private String contactPerson;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobile;

    @Email(message = "Email must be valid")
    private String email;

    @NotNull(message = "Party type is required")
    private PartyType partyType;

    private String gstNumber;

    private String panNumber;

    private String address;

    private Long cityId;

    private Long stateId;

    private Long countryId;

    private String pincode;

    private String notes;

    private Set<Long> dealsInCategoryIds = new HashSet<>();

    @Valid
    private List<PartyAddressRequest> addresses = new ArrayList<>();
}
