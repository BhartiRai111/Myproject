package com.storehub.dto;

import com.storehub.entity.PurchaseOrder;
import com.storehub.entity.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseOrderResponse {

    private Long id;
    private String orderNumber;
    private LocalDate orderDate;
    private SupplierResponse supplier;
    private String supplierPhone;
    private String supplierGstin;
    private String billingAddress;
    private String shippingAddress;
    private LocalDate expectedDeliveryDate;
    private String remarks;
    private PurchaseOrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal remainingAmount;
    private List<PurchaseOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseOrderResponse fromEntity(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = order.getItems().stream()
                .map(PurchaseOrderItemResponse::fromEntity)
                .toList();

        BigDecimal receivedAmount = order.getItems().stream()
                .map(i -> {
                    int receivedQty = i.getReceivedQuantity() == null ? 0 : i.getReceivedQuantity();
                    if (receivedQty <= 0 || i.getQuantity() == 0) {
                        return BigDecimal.ZERO;
                    }
                    return i.getTotalAmount()
                            .multiply(BigDecimal.valueOf(receivedQty))
                            .divide(BigDecimal.valueOf(i.getQuantity()), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = order.getTotalAmount().subtract(receivedAmount);
        if (remainingAmount.signum() < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        return PurchaseOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .supplier(order.getSupplier() != null ? SupplierResponse.fromEntity(order.getSupplier()) : null)
                .supplierPhone(order.getSupplierPhone())
                .supplierGstin(order.getSupplierGstin())
                .billingAddress(order.getBillingAddress())
                .shippingAddress(order.getShippingAddress())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .remarks(order.getRemarks())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .receivedAmount(receivedAmount)
                .remainingAmount(remainingAmount)
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
