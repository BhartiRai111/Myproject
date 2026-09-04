package com.storehub.dto;

import com.storehub.entity.Party;
import com.storehub.entity.PartyStatus;
import com.storehub.entity.PartyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class PartyResponse {
    private Long id;
    private String partyCode;
    private String partyName;
    private String contactPerson;
    private String mobile;
    private String email;
    private PartyType partyType;
    private String gstNumber;
    private String panNumber;
    private String address;
    private Long cityId;
    private String cityName;
    private Long stateId;
    private String stateName;
    private Long countryId;
    private String countryName;
    private String pincode;
    private PartyStatus status;
    private String notes;
    private Set<Long> dealsInCategoryIds;
    private Set<String> dealsInCategoryNames;
    private List<PartyAddressResponse> addresses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PartyResponse fromEntity(Party p) {
        return PartyResponse.builder()
                .id(p.getId())
                .partyCode(p.getPartyCode())
                .partyName(p.getPartyName())
                .contactPerson(p.getContactPerson())
                .mobile(p.getMobile())
                .email(p.getEmail())
                .partyType(p.getPartyType())
                .gstNumber(p.getGstNumber())
                .panNumber(p.getPanNumber())
                .address(p.getAddress())
                .cityId(p.getCity() != null ? p.getCity().getId() : null)
                .cityName(p.getCity() != null ? p.getCity().getName() : null)
                .stateId(p.getState() != null ? p.getState().getId() : null)
                .stateName(p.getState() != null ? p.getState().getName() : null)
                .countryId(p.getCountry() != null ? p.getCountry().getId() : null)
                .countryName(p.getCountry() != null ? p.getCountry().getName() : null)
                .pincode(p.getPincode())
                .status(p.getStatus())
                .notes(p.getNotes())
                .dealsInCategoryIds(p.getDealsInCategories().stream().map(com.storehub.entity.Category::getId)
                        .collect(java.util.stream.Collectors.toSet()))
                .dealsInCategoryNames(p.getDealsInCategories().stream().map(com.storehub.entity.Category::getName)
                        .collect(java.util.stream.Collectors.toSet()))
                .addresses(p.getAddresses().stream().map(PartyAddressResponse::fromEntity).toList())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
