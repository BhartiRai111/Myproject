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
public class TrialBalanceResponse {
    private LocalDate asOfDate;
    private List<TrialBalanceRow> rows;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal difference;
    private boolean balanced;
}
