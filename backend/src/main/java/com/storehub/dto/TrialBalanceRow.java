package com.storehub.dto;

import com.storehub.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class TrialBalanceRow {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private BigDecimal debit;
    private BigDecimal credit;
}
