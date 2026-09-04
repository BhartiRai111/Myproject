package com.storehub.dto;

import com.storehub.entity.Unit;
import com.storehub.entity.UnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UnitResponse {
    private Long id;
    private String name;
    private String symbol;
    private UnitStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UnitResponse fromEntity(Unit u) {
        return UnitResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .symbol(u.getSymbol())
                .status(u.getStatus())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
