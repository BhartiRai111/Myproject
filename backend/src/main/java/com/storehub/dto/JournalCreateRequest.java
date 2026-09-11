package com.storehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** Request body for a manual journal entry (POST /api/accounting/journals). */
@Getter
@Setter
public class JournalCreateRequest {

    @NotNull(message = "Journal date is required")
    private LocalDate journalDate;

    private String narration;

    @NotEmpty(message = "A journal must have at least one line")
    @Valid
    private List<JournalLineRequest> lines;
}
