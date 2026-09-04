package com.storehub.dto;

import com.storehub.entity.ItemGroup;
import com.storehub.entity.ItemGroupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ItemGroupResponse {
    private Long id;
    private String name;
    private String description;
    private ItemGroupStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ItemGroupResponse fromEntity(ItemGroup g) {
        return ItemGroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .status(g.getStatus())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
