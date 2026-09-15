package com.storehub.dto;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PartyLedgerResponse {
    private AccountingPartyType partyType;
    private Long partyId;
    private String partyName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private LedgerEntryType openingBalanceType;
    private List<PartyLedgerRow> rows;
    private BigDecimal closingBalance;
    private LedgerEntryType closingBalanceType;
}
