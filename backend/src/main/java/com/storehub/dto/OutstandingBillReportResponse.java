package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bill-wise Outstanding + Ageing (spec sections 14-15). {@code ageingBasis} is
 * always {@code "INVOICE_DATE"}: no due date, credit-period, or credit-terms
 * field exists anywhere on Sale/Purchase/Receipt/Payment/ReceiptAllocation/
 * PaymentAllocation in this codebase (confirmed absent by inspection), so age
 * is computed from the invoice date — this is stated explicitly here rather
 * than silently assumed, per the spec's own instruction.
 */
@Getter
@Builder
@AllArgsConstructor
public class OutstandingBillReportResponse {
    private AccountingPartyType partyType;
    private LocalDate asOfDate;
    private String ageingBasis;
    private List<OutstandingBillDetailRow> rows;
    private List<AgeingBucketSummary> ageingSummary;
    private BigDecimal totalInvoiceAmount;
    private BigDecimal totalReceivedOrPaid;
    private BigDecimal totalOutstanding;
}
