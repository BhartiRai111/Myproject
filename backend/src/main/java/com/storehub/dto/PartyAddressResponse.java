package com.storehub.dto;

import com.storehub.entity.PartyAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PartyAddressResponse {
    private Long id;
    private String label;
    private String addressLine;
    private Long cityId;
    private String cityName;
    private Long stateId;
    private String stateName;
    private String pincode;

    public static PartyAddressResponse fromEntity(PartyAddress a) {
        return PartyAddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .addressLine(a.getAddressLine())
                .cityId(a.getCity() != null ? a.getCity().getId() : null)
                .cityName(a.getCity() != null ? a.getCity().getName() : null)
                .stateId(a.getState() != null ? a.getState().getId() : null)
                .stateName(a.getState() != null ? a.getState().getName() : null)
                .pincode(a.getPincode())
                .build();
    }
}
