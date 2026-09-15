package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class FinancialYearSummaryResponse {
    private FinancialYearResponse financialYear;
    private BigDecimal totalSales;
    private BigDecimal totalPurchases;
    private long saleCount;
    private long purchaseCount;
}
