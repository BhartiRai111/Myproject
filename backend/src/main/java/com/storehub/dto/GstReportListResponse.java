package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** Shared shape for the Output GST Report (sales) and Input GST Report (purchases) pages. */
@Getter
@Builder
@AllArgsConstructor
public class GstReportListResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private PagedResponse<GstTransactionRow> transactions;
    private GstSummaryTotals totals;
}
