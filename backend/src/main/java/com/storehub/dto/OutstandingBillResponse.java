package com.storehub.dto;

import com.storehub.entity.Sale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class OutstandingBillResponse {

    private Long saleId;
    private String invoiceNumber;
    private LocalDate saleDate;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;

    public static OutstandingBillResponse fromEntity(Sale sale) {
        return OutstandingBillResponse.builder()
                .saleId(sale.getId())
                .invoiceNumber(sale.getInvoiceNumber())
                .saleDate(sale.getSaleDate())
                .totalAmount(sale.getTotalAmount())
                .paidAmount(sale.getPaidAmount())
                .dueAmount(sale.getDueAmount())
                .build();
    }
}
