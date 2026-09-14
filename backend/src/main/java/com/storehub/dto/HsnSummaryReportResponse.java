package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class HsnSummaryReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<HsnSummaryRow> outward;
    private List<HsnSummaryRow> inward;
}
