package com.storehub.dto;

import com.storehub.entity.City;
import com.storehub.entity.CityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CityResponse {
    private Long id;
    private String name;
    private Long stateId;
    private String stateName;
    private Long countryId;
    private String countryName;
    private CityStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CityResponse fromEntity(City c) {
        return CityResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .stateId(c.getState().getId())
                .stateName(c.getState().getName())
                .countryId(c.getState().getCountry().getId())
                .countryName(c.getState().getCountry().getName())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
