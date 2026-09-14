package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class GstSummaryTotals {
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTax;
    private BigDecimal totalValue;
    private long transactionCount;

    public static GstSummaryTotals zero() {
        return GstSummaryTotals.builder()
                .taxableAmount(BigDecimal.ZERO)
                .cgstAmount(BigDecimal.ZERO)
                .sgstAmount(BigDecimal.ZERO)
                .igstAmount(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .totalValue(BigDecimal.ZERO)
                .transactionCount(0)
                .build();
    }
}
