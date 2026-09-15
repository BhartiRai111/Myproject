package com.storehub.dto;

import com.storehub.entity.DebitNoteType;
import com.storehub.entity.StockImpactType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class DebitNoteCreateRequest {

    @NotNull(message = "Source purchase is required")
    private Long sourcePurchaseId;

    @NotNull(message = "Note type is required")
    private DebitNoteType noteType;

    @NotNull(message = "Note date is required")
    private LocalDate noteDate;

    @NotNull(message = "Stock impact is required")
    private StockImpactType stockImpact;

    private String reason;

    private String remarks;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<DebitNoteItemRequest> items;

    private boolean post;
}
