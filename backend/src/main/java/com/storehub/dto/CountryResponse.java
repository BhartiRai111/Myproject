package com.storehub.dto;

import com.storehub.entity.Country;
import com.storehub.entity.CountryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CountryResponse {
    private Long id;
    private String name;
    private String code;
    private CountryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CountryResponse fromEntity(Country c) {
        return CountryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
