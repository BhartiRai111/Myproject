package com.storehub.dto;

import com.storehub.entity.Account;
import com.storehub.entity.AccountType;
import com.storehub.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class AccountResponse {
    private Long id;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private Long accountGroupId;
    private String accountGroupName;
    private BigDecimal openingBalance;
    private LedgerEntryType openingBalanceType;
    private boolean systemAccount;
    private boolean active;
    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime updatedAt;

    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType())
                .accountGroupId(account.getAccountGroup() != null ? account.getAccountGroup().getId() : null)
                .accountGroupName(account.getAccountGroup() != null ? account.getAccountGroup().getName() : null)
                .openingBalance(account.getOpeningBalance())
                .openingBalanceType(account.getOpeningBalanceType())
                .systemAccount(account.isSystemAccount())
                .active(account.isActive())
                .createdBy(account.getCreatedBy())
                .createdAt(account.getCreatedAt())
                .modifiedBy(account.getModifiedBy())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
