package com.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class HsnTaxRateRequest {

    @NotNull(message = "Tax percent is required")
    private BigDecimal taxPercent;

    private BigDecimal cgstPercent;

    private BigDecimal sgstPercent;

    private BigDecimal igstPercent;

    private BigDecimal cessPercent;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;
}
