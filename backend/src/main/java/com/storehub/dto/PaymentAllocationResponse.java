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
    private Long expenseId;
    private String expenseNumber;
    private BigDecimal amountApplied;

    public static PaymentAllocationResponse fromEntity(PaymentAllocation allocation) {
        return PaymentAllocationResponse.builder()
                .id(allocation.getId())
                .purchaseId(allocation.getPurchase() != null ? allocation.getPurchase().getId() : null)
                .purchaseNumber(allocation.getPurchase() != null ? allocation.getPurchase().getPurchaseNumber() : null)
                .expenseId(allocation.getExpense() != null ? allocation.getExpense().getId() : null)
                .expenseNumber(allocation.getExpense() != null ? allocation.getExpense().getExpenseNumber() : null)
                .amountApplied(allocation.getAmountApplied())
                .build();
    }
}
