package com.storehub.dto;

import com.storehub.entity.HealthCheckStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class HealthCheckFinding {
    private String checkName;
    private HealthCheckStatus status;
    private String message;
    private List<String> details;
}
