package com.storehub.dto;

import com.storehub.entity.DayClosing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class DayClosingResponse {

    private LocalDate closingDate;
    private boolean closed;

    private BigDecimal totalSales;
    private BigDecimal totalPurchases;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal upiSales;
    private BigDecimal creditSales;
    private BigDecimal totalReceipts;
    private BigDecimal totalPayments;
    private BigDecimal totalExpenses;
    private BigDecimal cashIn;
    private BigDecimal cashOut;
    private BigDecimal expectedCash;

    private BigDecimal actualCash;
    private BigDecimal difference;
    private String differenceReason;
    private String closedBy;
    private LocalDateTime closedAt;

    public static DayClosingResponse fromClosed(DayClosing d, DayClosingResponse computed) {
        return DayClosingResponse.builder()
                .closingDate(d.getClosingDate())
                .closed(true)
                .totalSales(computed.getTotalSales())
                .totalPurchases(computed.getTotalPurchases())
                .cashSales(computed.getCashSales())
                .cardSales(computed.getCardSales())
                .upiSales(computed.getUpiSales())
                .creditSales(computed.getCreditSales())
                .totalReceipts(computed.getTotalReceipts())
                .totalPayments(computed.getTotalPayments())
                .totalExpenses(computed.getTotalExpenses())
                .cashIn(computed.getCashIn())
                .cashOut(computed.getCashOut())
                .expectedCash(d.getExpectedCash())
                .actualCash(d.getActualCash())
                .difference(d.getDifference())
                .differenceReason(d.getDifferenceReason())
                .closedBy(d.getClosedBy())
                .closedAt(d.getClosedAt())
                .build();
    }
}
