package com.storehub.dto;

import com.storehub.entity.State;
import com.storehub.entity.StateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class StateResponse {
    private Long id;
    private String name;
    private String code;
    private Long countryId;
    private String countryName;
    private StateStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StateResponse fromEntity(State s) {
        return StateResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .code(s.getCode())
                .countryId(s.getCountry().getId())
                .countryName(s.getCountry().getName())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
