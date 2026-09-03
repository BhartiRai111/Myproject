package com.storehub.dto;

import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SaleResponse {

    private Long id;
    private String invoiceNumber;
    private CustomerResponse customer;
    private LocalDate saleDate;
    private List<SaleItemResponse> items;
    private BigDecimal subtotalAmount;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private SaleStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SaleResponse fromEntity(Sale sale) {
        List<SaleItemResponse> items = sale.getItems().stream()
                .map(SaleItemResponse::fromEntity)
                .toList();

        BigDecimal subtotalAmount = sale.getItems().stream()
                .map(item -> item.getSellingPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = sale.getItems().stream()
                .map(item -> item.getDiscount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = sale.getItems().stream()
                .map(item -> item.getTax())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SaleResponse.builder()
                .id(sale.getId())
                .invoiceNumber(sale.getInvoiceNumber())
                .customer(sale.getCustomer() != null ? CustomerResponse.fromEntity(sale.getCustomer()) : null)
                .saleDate(sale.getSaleDate())
                .items(items)
                .subtotalAmount(subtotalAmount)
                .totalDiscount(totalDiscount)
                .totalTax(totalTax)
                .totalAmount(sale.getTotalAmount())
                .paymentStatus(sale.getPaymentStatus())
                .status(sale.getStatus())
                .notes(sale.getNotes())
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}
