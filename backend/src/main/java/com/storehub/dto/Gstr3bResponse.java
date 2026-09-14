package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * GSTR-3B summary — a reporting/preparation aid computed from this app's own
 * GST-reportable transactions. It is NOT an actual government filing integration;
 * nothing here is submitted to the GST portal.
 */
@Getter
@Builder
@AllArgsConstructor
public class Gstr3bResponse {
    private String returnPeriod;
    private GstSummaryTotals outwardSupplies;
    private GstSummaryTotals inputTaxCredit;
    private GstSummaryTotals netLiability;
    private String note;
}
