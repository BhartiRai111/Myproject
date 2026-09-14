package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single row in a GST report (GSTR-1, Purchase GST Report, Output/Input GST Report),
 * built directly from a {@link com.storehub.entity.GstTransaction} — never recalculated.
 * {@code itcEligible} is only meaningful for PURCHASE rows (ITC can only be claimed on a
 * B2B purchase with a valid supplier GSTIN on record); it is null for SALE rows.
 */
@Getter
@Builder
@AllArgsConstructor
public class GstTransactionRow {
    private Long gstTransactionId;
    private VoucherType sourceTransactionType;
    private Long sourceTransactionId;
    private String voucherNumber;
    private LocalDate voucherDate;
    private AccountingPartyType partyType;
    private Long partyId;
    private String partyName;
    private String partyGstin;
    private String placeOfSupplyStateCode;
    private boolean b2b;
    private Boolean itcEligible;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTax;
    private BigDecimal totalValue;
    private String returnPeriod;
}
