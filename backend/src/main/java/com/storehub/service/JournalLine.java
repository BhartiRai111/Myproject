package com.storehub.service;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.SystemAccountCode;

import java.math.BigDecimal;

/** One debit-or-credit line to be posted by {@link AccountingService#postJournal}. */
public record JournalLine(SystemAccountCode account, BigDecimal debitAmount, BigDecimal creditAmount,
                           String narration, AccountingPartyType partyType, Long partyId) {

    public static JournalLine debit(SystemAccountCode account, BigDecimal amount) {
        return new JournalLine(account, amount, BigDecimal.ZERO, null, null, null);
    }

    public static JournalLine debit(SystemAccountCode account, BigDecimal amount, AccountingPartyType partyType, Long partyId) {
        return new JournalLine(account, amount, BigDecimal.ZERO, null, partyType, partyId);
    }

    public static JournalLine credit(SystemAccountCode account, BigDecimal amount) {
        return new JournalLine(account, BigDecimal.ZERO, amount, null, null, null);
    }

    public static JournalLine credit(SystemAccountCode account, BigDecimal amount, AccountingPartyType partyType, Long partyId) {
        return new JournalLine(account, BigDecimal.ZERO, amount, null, partyType, partyId);
    }
}
