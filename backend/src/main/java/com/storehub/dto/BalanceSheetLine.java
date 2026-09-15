package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class BalanceSheetLine {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal amount;
}
