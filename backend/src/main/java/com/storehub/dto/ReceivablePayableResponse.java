package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ReceivablePayableResponse {
    private AccountingPartyType partyType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<ReceivablePayableRow> rows;
    private BigDecimal totalOpening;
    private BigDecimal totalTransactions;
    private BigDecimal totalPayments;
    private BigDecimal totalOutstanding;
}
