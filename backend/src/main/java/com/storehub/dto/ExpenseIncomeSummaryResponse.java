package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseIncomeSummaryResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<ExpenseIncomeAccountGroup> accounts;
    private BigDecimal totalAmount;
}
