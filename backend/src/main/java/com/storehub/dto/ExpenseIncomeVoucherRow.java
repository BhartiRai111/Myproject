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
public class ExpenseIncomeVoucherRow {
    private LocalDate voucherDate;
    private Long journalId;
    private String journalNumber;
    private VoucherType voucherType;
    private String voucherNumber;
    private String narration;
    private BigDecimal amount;
}
