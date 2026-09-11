package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Balances of the core control accounts, used by the Accounting Dashboard. */
@Getter
@Builder
@AllArgsConstructor
public class AccountingDashboardResponse {
    private BigDecimal cashBalance;
    private BigDecimal bankBalance;
    private BigDecimal receivableBalance;
    private BigDecimal payableBalance;
}
