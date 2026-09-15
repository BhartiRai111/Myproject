package com.storehub.dto;

import com.storehub.entity.PaymentMethod;
import com.storehub.entity.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class PaymentMethodResponse {
    private Long id;
    private String name;
    private PaymentMode type;
    private boolean active;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentMethodResponse fromEntity(PaymentMethod p) {
        return PaymentMethodResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .type(p.getType())
                .active(p.isActive())
                .sortOrder(p.getSortOrder())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
