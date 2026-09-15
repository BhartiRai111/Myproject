package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One party's row in the Receivable/Payable report. {@code creditNoteAmount}
 * (Receivable) / {@code debitNoteAmount} (Payable) are populated from the
 * same journal-based control-account movement as everything else here (see
 * ReceivablePayableService), added in Phase 5 once Credit/Debit Notes
 * existed to post one; the other of the pair stays zero since a Sales
 * Credit Note only ever affects Receivable and a Purchase Debit Note only
 * ever affects Payable.
 */
@Getter
@Builder
@AllArgsConstructor
public class ReceivablePayableRow {
    private Long partyId;
    private String partyName;
    private BigDecimal openingBalance;
    private BigDecimal transactionAmount;
    private BigDecimal paymentAmount;
    private BigDecimal creditNoteAmount;
    private BigDecimal debitNoteAmount;
    private BigDecimal closingOutstanding;
}
