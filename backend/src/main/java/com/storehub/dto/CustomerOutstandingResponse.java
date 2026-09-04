package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CustomerOutstandingResponse {

    private Long customerId;
    private BigDecimal totalOutstanding;
    private List<OutstandingBillResponse> bills;
}
