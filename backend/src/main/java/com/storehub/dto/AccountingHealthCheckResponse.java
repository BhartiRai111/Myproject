package com.storehub.dto;

import com.storehub.entity.HealthCheckStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AccountingHealthCheckResponse {
    private LocalDateTime generatedAt;
    private HealthCheckStatus overallStatus;
    private List<HealthCheckFinding> findings;
}
