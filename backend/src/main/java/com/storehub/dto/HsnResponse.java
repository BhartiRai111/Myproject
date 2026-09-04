package com.storehub.dto;

import com.storehub.entity.Hsn;
import com.storehub.entity.HsnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class HsnResponse {
    private Long id;
    private String hsnCode;
    private String description;
    private HsnStatus status;
    private List<HsnTaxRateResponse> taxRates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static HsnResponse fromEntity(Hsn h) {
        return HsnResponse.builder()
                .id(h.getId())
                .hsnCode(h.getHsnCode())
                .description(h.getDescription())
                .status(h.getStatus())
                .taxRates(h.getTaxRates().stream().map(HsnTaxRateResponse::fromEntity).toList())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
