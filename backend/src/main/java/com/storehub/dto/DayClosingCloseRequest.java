package com.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class DayClosingCloseRequest {

    @NotNull(message = "Closing date is required")
    private LocalDate closingDate;

    @NotNull(message = "Actual cash counted is required")
    private BigDecimal actualCash;

    private String differenceReason;
}
