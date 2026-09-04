package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseSummaryResponse {

    private long totalPurchaseOrders;
    private long pendingOrders;
    private long totalPurchaseBills;
    private BigDecimal totalPayables;
    private BigDecimal todaysPurchases;
}
