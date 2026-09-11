package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DayBookResponse {
    private List<DayBookRow> rows;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
}
