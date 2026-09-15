package com.storehub.dto;

import com.storehub.entity.CreditNoteType;
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
public class CreditNoteCreateRequest {

    @NotNull(message = "Source sale is required")
    private Long sourceSaleId;

    @NotNull(message = "Note type is required")
    private CreditNoteType noteType;

    @NotNull(message = "Note date is required")
    private LocalDate noteDate;

    @NotNull(message = "Stock impact is required")
    private StockImpactType stockImpact;

    private String reason;

    private String remarks;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreditNoteItemRequest> items;

    /** If true, posts immediately after creation; otherwise saved as DRAFT. */
    private boolean post;
}
