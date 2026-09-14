package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ReconciliationResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<ReconciliationRow> rows;
    private long matchedCount;
    private long mismatchedCount;
    private long missingCount;
    private long duplicateCount;
}
