package com.storehub.dto;

import com.storehub.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the Cash Book / Bank Book: a debit into the account is a receipt, a credit out of it is a payment. */
@Getter
@Builder
@AllArgsConstructor
public class CashBankBookRow {
    private LocalDate voucherDate;
    private VoucherType voucherType;
    private String voucherNumber;
    private String particulars;
    private BigDecimal receipt;
    private BigDecimal payment;
    private BigDecimal runningBalance;
}
