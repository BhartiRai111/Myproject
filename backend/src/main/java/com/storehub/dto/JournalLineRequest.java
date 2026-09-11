package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class JournalLineRequest {

    @NotNull(message = "Account is required for every journal line")
    private Long accountId;

    private BigDecimal debitAmount = BigDecimal.ZERO;

    private BigDecimal creditAmount = BigDecimal.ZERO;

    private String narration;

    private AccountingPartyType partyType;

    private Long partyId;
}
