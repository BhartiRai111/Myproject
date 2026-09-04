package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SalesSummaryResponse {

    private long totalSalesOrders;
    private long pendingOrders;
    private long totalSalesBills;
    private BigDecimal totalReceivables;
    private BigDecimal todaysSales;
}
