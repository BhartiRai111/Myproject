package com.storehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class SalesOrderRequest {

    private Long customerId;

    private String customerPhone;

    private String customerGstin;

    private String billingAddress;

    private String shippingAddress;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    private String remarks;

    @NotEmpty(message = "At least one order item is required")
    @Valid
    private List<SalesOrderItemRequest> items;
}
