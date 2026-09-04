package com.storehub.dto;

import com.storehub.entity.GstType;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.TaxMode;
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
    private GstType gstType;
    private TaxMode taxMode;
    private String customerPhone;
    private String customerGstin;
    private String billingAddress;
    private String shippingAddress;
    private PaymentMode paymentMode;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private Long salesOrderId;
    private String salesOrderNumber;
    private boolean hasReceipts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SaleResponse fromEntity(Sale sale) {
        return fromEntity(sale, false);
    }

    public static SaleResponse fromEntity(Sale sale, boolean hasReceipts) {
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
                .gstType(sale.getGstType())
                .taxMode(sale.getTaxMode())
                .customerPhone(sale.getCustomerPhone())
                .customerGstin(sale.getCustomerGstin())
                .billingAddress(sale.getBillingAddress())
                .shippingAddress(sale.getShippingAddress())
                .paymentMode(sale.getPaymentMode())
                .paidAmount(sale.getPaidAmount())
                .dueAmount(sale.getDueAmount())
                .taxableAmount(sale.getTaxableAmount())
                .cgstAmount(sale.getCgstAmount())
                .sgstAmount(sale.getSgstAmount())
                .igstAmount(sale.getIgstAmount())
                .salesOrderId(sale.getSalesOrder() != null ? sale.getSalesOrder().getId() : null)
                .salesOrderNumber(sale.getSalesOrder() != null ? sale.getSalesOrder().getOrderNumber() : null)
                .hasReceipts(hasReceipts)
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}
