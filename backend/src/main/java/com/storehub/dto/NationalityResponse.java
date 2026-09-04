package com.storehub.dto;

import com.storehub.entity.Nationality;
import com.storehub.entity.NationalityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class NationalityResponse {
    private Long id;
    private String name;
    private Long countryId;
    private String countryName;
    private NationalityStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NationalityResponse fromEntity(Nationality n) {
        return NationalityResponse.builder()
                .id(n.getId())
                .name(n.getName())
                .countryId(n.getCountry().getId())
                .countryName(n.getCountry().getName())
                .status(n.getStatus())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
