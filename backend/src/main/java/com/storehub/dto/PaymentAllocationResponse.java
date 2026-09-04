package com.storehub.dto;

import com.storehub.entity.PaymentAllocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class PaymentAllocationResponse {

    private Long id;
    private Long purchaseId;
    private String purchaseNumber;
    private BigDecimal amountApplied;

    public static PaymentAllocationResponse fromEntity(PaymentAllocation allocation) {
        return PaymentAllocationResponse.builder()
                .id(allocation.getId())
                .purchaseId(allocation.getPurchase().getId())
                .purchaseNumber(allocation.getPurchase().getPurchaseNumber())
                .amountApplied(allocation.getAmountApplied())
                .build();
    }
}
