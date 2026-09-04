package com.storehub.dto;

import com.storehub.entity.SalesOrder;
import com.storehub.entity.SalesOrderStatus;
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
public class SalesOrderResponse {

    private Long id;
    private String orderNumber;
    private LocalDate orderDate;
    private CustomerResponse customer;
    private String customerPhone;
    private String customerGstin;
    private String billingAddress;
    private String shippingAddress;
    private LocalDate expectedDeliveryDate;
    private String remarks;
    private SalesOrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal billedAmount;
    private BigDecimal remainingAmount;
    private List<SalesOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalesOrderResponse fromEntity(SalesOrder order) {
        List<SalesOrderItemResponse> items = order.getItems().stream()
                .map(SalesOrderItemResponse::fromEntity)
                .toList();

        BigDecimal billedAmount = order.getItems().stream()
                .map(i -> {
                    int billedQty = i.getBilledQuantity() == null ? 0 : i.getBilledQuantity();
                    if (billedQty <= 0 || i.getQuantity() == 0) {
                        return BigDecimal.ZERO;
                    }
                    return i.getTotalAmount()
                            .multiply(BigDecimal.valueOf(billedQty))
                            .divide(BigDecimal.valueOf(i.getQuantity()), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = order.getTotalAmount().subtract(billedAmount);
        if (remainingAmount.signum() < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        return SalesOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .customer(order.getCustomer() != null ? CustomerResponse.fromEntity(order.getCustomer()) : null)
                .customerPhone(order.getCustomerPhone())
                .customerGstin(order.getCustomerGstin())
                .billingAddress(order.getBillingAddress())
                .shippingAddress(order.getShippingAddress())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .remarks(order.getRemarks())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .billedAmount(billedAmount)
                .remainingAmount(remainingAmount)
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
