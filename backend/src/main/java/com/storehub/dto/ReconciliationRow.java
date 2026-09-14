package com.storehub.dto;

import com.storehub.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class ReconciliationRow {
    private VoucherType sourceTransactionType;
    private Long sourceTransactionId;
    private String voucherNumber;
    private LocalDate voucherDate;
    private String status;
    private BigDecimal sourceTaxableAmount;
    private BigDecimal reportedTaxableAmount;
    private BigDecimal sourceTotalTax;
    private BigDecimal reportedTotalTax;
    private String remarks;
}
