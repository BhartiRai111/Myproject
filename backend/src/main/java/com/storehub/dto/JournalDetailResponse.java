package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class JournalDetailResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String narration;
    private AccountingPartyType partyType;
    private Long partyId;
    private VoucherType referenceType;
    private Long referenceId;

    public static JournalDetailResponse fromEntity(JournalDetail line) {
        return JournalDetailResponse.builder()
                .id(line.getId())
                .accountId(line.getAccount().getId())
                .accountCode(line.getAccount().getAccountCode())
                .accountName(line.getAccount().getAccountName())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .narration(line.getNarration())
                .partyType(line.getPartyType())
                .partyId(line.getPartyId())
                .referenceType(line.getReferenceType())
                .referenceId(line.getReferenceId())
                .build();
    }
}
