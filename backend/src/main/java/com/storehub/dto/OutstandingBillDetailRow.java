package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class OutstandingBillDetailRow {
    private Long partyId;
    private String partyName;
    private Long billId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
    private BigDecimal receivedOrPaidAmount;
    private BigDecimal outstanding;
    private long daysOutstanding;
    private String ageingBucket;
}
