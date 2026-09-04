package com.storehub.dto;

import com.storehub.entity.GstType;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
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
public class PurchaseResponse {

    private Long id;
    private String purchaseNumber;
    private SupplierResponse supplier;
    private LocalDate purchaseDate;
    private List<PurchaseItemResponse> items;
    private BigDecimal subtotalAmount;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private PurchaseStatus status;
    private String notes;
    private GstType gstType;
    private TaxMode taxMode;
    private String supplierPhone;
    private String supplierGstin;
    private String billingAddress;
    private String shippingAddress;
    private PaymentMode paymentMode;
    private BigDecimal paidAmount;
    private BigDecimal payableAmount;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private Long purchaseOrderId;
    private String purchaseOrderNumber;
    private boolean hasPayments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseResponse fromEntity(Purchase purchase) {
        return fromEntity(purchase, false);
    }

    public static PurchaseResponse fromEntity(Purchase purchase, boolean hasPayments) {
        List<PurchaseItemResponse> items = purchase.getItems().stream()
                .map(PurchaseItemResponse::fromEntity)
                .toList();

        BigDecimal subtotalAmount = purchase.getItems().stream()
                .map(item -> item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = purchase.getItems().stream()
                .map(item -> item.getDiscount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = purchase.getItems().stream()
                .map(item -> item.getTax())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PurchaseResponse.builder()
                .id(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .supplier(SupplierResponse.fromEntity(purchase.getSupplier()))
                .purchaseDate(purchase.getPurchaseDate())
                .items(items)
                .subtotalAmount(subtotalAmount)
                .totalDiscount(totalDiscount)
                .totalTax(totalTax)
                .totalAmount(purchase.getTotalAmount())
                .paymentStatus(purchase.getPaymentStatus())
                .status(purchase.getStatus())
                .notes(purchase.getNotes())
                .gstType(purchase.getGstType())
                .taxMode(purchase.getTaxMode())
                .supplierPhone(purchase.getSupplierPhone())
                .supplierGstin(purchase.getSupplierGstin())
                .billingAddress(purchase.getBillingAddress())
                .shippingAddress(purchase.getShippingAddress())
                .paymentMode(purchase.getPaymentMode())
                .paidAmount(purchase.getPaidAmount())
                .payableAmount(purchase.getPayableAmount())
                .taxableAmount(purchase.getTaxableAmount())
                .cgstAmount(purchase.getCgstAmount())
                .sgstAmount(purchase.getSgstAmount())
                .igstAmount(purchase.getIgstAmount())
                .purchaseOrderId(purchase.getPurchaseOrder() != null ? purchase.getPurchaseOrder().getId() : null)
                .purchaseOrderNumber(purchase.getPurchaseOrder() != null ? purchase.getPurchaseOrder().getOrderNumber() : null)
                .hasPayments(hasPayments)
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .build();
    }
}
