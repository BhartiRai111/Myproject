package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseSummaryGroupRow {
    private String key;
    private String label;
    private BigDecimal totalAmount;
    private long count;
}
