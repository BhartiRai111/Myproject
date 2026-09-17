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
public class StockTransferRequest {

    @NotNull(message = "Transfer date is required")
    private LocalDate transferDate;

    @NotNull(message = "Source store is required")
    private Long fromStoreId;

    @NotNull(message = "Destination store is required")
    private Long toStoreId;

    private String remarks;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<StockTransferItemRequest> items;
}
