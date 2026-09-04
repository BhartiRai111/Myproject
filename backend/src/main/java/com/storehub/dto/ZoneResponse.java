package com.storehub.dto;

import com.storehub.entity.Zone;
import com.storehub.entity.ZoneStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ZoneResponse {
    private Long id;
    private String name;
    private String code;
    private Long countryId;
    private String countryName;
    private Long stateId;
    private String stateName;
    private ZoneStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ZoneResponse fromEntity(Zone z) {
        return ZoneResponse.builder()
                .id(z.getId())
                .name(z.getName())
                .code(z.getCode())
                .countryId(z.getCountry() != null ? z.getCountry().getId() : null)
                .countryName(z.getCountry() != null ? z.getCountry().getName() : null)
                .stateId(z.getState() != null ? z.getState().getId() : null)
                .stateName(z.getState() != null ? z.getState().getName() : null)
                .status(z.getStatus())
                .createdAt(z.getCreatedAt())
                .updatedAt(z.getUpdatedAt())
                .build();
    }
}
