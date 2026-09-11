package com.storehub.dto;

import com.storehub.entity.AccountType;
import com.storehub.entity.LedgerEntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountRequest {

    @NotBlank(message = "Account code is required")
    private String accountCode;

    @NotBlank(message = "Account name is required")
    private String accountName;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    private Long accountGroupId;

    @NotNull(message = "Opening balance is required")
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @NotNull(message = "Opening balance type is required")
    private LedgerEntryType openingBalanceType = LedgerEntryType.DEBIT;

    private boolean active = true;
}
