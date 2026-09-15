package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseIncomeAccountGroup {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal total;
    private List<ExpenseIncomeVoucherRow> vouchers;
}
