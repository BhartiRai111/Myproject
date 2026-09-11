package com.storehub.service;

import com.storehub.entity.AccountingPartyType;

import java.math.BigDecimal;

/** One line of a manually-entered journal (POST /api/accounting/journals), referencing a real account id. */
public record ManualJournalLine(Long accountId, BigDecimal debitAmount, BigDecimal creditAmount,
                                 String narration, AccountingPartyType partyType, Long partyId) {
}
