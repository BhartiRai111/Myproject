package com.storehub.dto;

import com.storehub.entity.FinancialYear;
import com.storehub.entity.FinancialYearStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class FinancialYearResponse {
    private Long id;
    private String name;
    private String code;
    private LocalDate startDate;
    private LocalDate endDate;
    private FinancialYearStatus status;
    private boolean current;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FinancialYearResponse fromEntity(FinancialYear fy) {
        return FinancialYearResponse.builder()
                .id(fy.getId())
                .name(fy.getName())
                .code(fy.getCode())
                .startDate(fy.getStartDate())
                .endDate(fy.getEndDate())
                .status(fy.getStatus())
                .current(fy.isCurrent())
                .createdAt(fy.getCreatedAt())
                .updatedAt(fy.getUpdatedAt())
                .build();
    }
}
