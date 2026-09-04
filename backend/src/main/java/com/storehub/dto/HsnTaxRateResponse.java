package com.storehub.dto;

import com.storehub.entity.HsnTaxRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class HsnTaxRateResponse {
    private Long id;
    private BigDecimal taxPercent;
    private BigDecimal cgstPercent;
    private BigDecimal sgstPercent;
    private BigDecimal igstPercent;
    private BigDecimal cessPercent;
    private LocalDate effectiveFrom;

    public static HsnTaxRateResponse fromEntity(HsnTaxRate r) {
        return HsnTaxRateResponse.builder()
                .id(r.getId())
                .taxPercent(r.getTaxPercent())
                .cgstPercent(r.getCgstPercent())
                .sgstPercent(r.getSgstPercent())
                .igstPercent(r.getIgstPercent())
                .cessPercent(r.getCessPercent())
                .effectiveFrom(r.getEffectiveFrom())
                .build();
    }
}
