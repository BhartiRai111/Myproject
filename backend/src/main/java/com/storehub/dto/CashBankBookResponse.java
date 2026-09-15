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
public class CashBankBookResponse {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private List<CashBankBookRow> rows;
    private BigDecimal totalReceipts;
    private BigDecimal totalPayments;
    private BigDecimal closingBalance;
}
