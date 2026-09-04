package com.storehub.dto;

import com.storehub.entity.ReceiptAllocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class ReceiptAllocationResponse {

    private Long id;
    private Long saleId;
    private String invoiceNumber;
    private BigDecimal amountApplied;

    public static ReceiptAllocationResponse fromEntity(ReceiptAllocation allocation) {
        return ReceiptAllocationResponse.builder()
                .id(allocation.getId())
                .saleId(allocation.getSale().getId())
                .invoiceNumber(allocation.getSale().getInvoiceNumber())
                .amountApplied(allocation.getAmountApplied())
                .build();
    }
}
