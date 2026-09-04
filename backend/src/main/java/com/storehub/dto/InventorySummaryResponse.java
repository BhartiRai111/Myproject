package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InventorySummaryResponse {
    private long totalProducts;
    private long totalStockUnits;
    private long lowStockCount;
    private long outOfStockCount;
}
