package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class StoreComparisonRow {
    private Long storeId;
    private String storeName;
    private String storeCode;
    private BigDecimal totalSales;
    private long salesCount;
    private BigDecimal totalPurchases;
    private long purchaseCount;
    private long totalStockUnits;
    private long lowStockCount;
    private long outOfStockCount;
}
