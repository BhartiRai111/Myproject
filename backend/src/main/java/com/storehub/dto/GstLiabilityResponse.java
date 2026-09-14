package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class GstLiabilityResponse {
    private String returnPeriod;
    private BigDecimal outputCgst;
    private BigDecimal outputSgst;
    private BigDecimal outputIgst;
    private BigDecimal outputTotal;
    private BigDecimal inputCgst;
    private BigDecimal inputSgst;
    private BigDecimal inputIgst;
    private BigDecimal inputTotal;
    private BigDecimal netCgst;
    private BigDecimal netSgst;
    private BigDecimal netIgst;
    private BigDecimal netTotal;
}
