package com.storehub.dto;

import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class PartyLedgerRow {
    private LocalDate voucherDate;
    private VoucherType voucherType;
    private String voucherNumber;
    private String particulars;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balance;
    private LedgerEntryType balanceType;
}
