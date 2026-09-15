package com.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FinancialYearRequest {

    /** Optional — defaults to "FY <label>" derived from startDate. */
    private String name;

    /** Optional — defaults to the "26-27" short code derived from startDate. */
    private String code;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
