package com.storehub.dto;

import com.storehub.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AccountLedgerResponse {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal openingBalance;
    private LedgerEntryType openingBalanceType;
    private List<AccountLedgerRow> rows;
    private BigDecimal closingBalance;
    private LedgerEntryType closingBalanceType;
}
