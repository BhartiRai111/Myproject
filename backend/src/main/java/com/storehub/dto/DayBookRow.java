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
public class DayBookRow {
    private LocalDate journalDate;
    private Long journalId;
    private String journalNumber;
    private VoucherType voucherType;
    private String voucherNumber;
    private String narration;
    private BigDecimal debit;
    private BigDecimal credit;
}
