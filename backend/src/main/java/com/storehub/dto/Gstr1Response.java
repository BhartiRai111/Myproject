package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * GSTR-1 (outward supplies) reporting/preparation aid — not an actual GSTN filing
 * integration. Credit Note / Debit Note sections are structurally present but always
 * empty: no Credit Note / Debit Note transaction type exists anywhere in this app.
 */
@Getter
@Builder
@AllArgsConstructor
public class Gstr1Response {
    private String returnPeriod;
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<GstTransactionRow> b2bTransactions;
    private List<GstTransactionRow> b2cTransactions;
    private List<GstTransactionRow> creditNotes;
    private List<GstTransactionRow> debitNotes;
    private List<HsnSummaryRow> hsnSummary;
    private GstSummaryTotals totals;
}
