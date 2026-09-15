package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class AgeingBucketSummary {
    private String bucket;
    private long count;
    private BigDecimal amount;
}
