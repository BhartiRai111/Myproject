package com.storehub.service;

import com.storehub.entity.CreditNote;
import com.storehub.entity.DebitNote;
import com.storehub.entity.NoteStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleStatus;

/**
 * The single source of truth for "does this transaction belong in GST return
 * reporting" (GSTR-1/GSTR-3B, built in Phase 3). GST TAX CALCULATION is not
 * the same thing as GST RETURN REPORTING: eligibility requires BOTH the
 * explicit {@code gstReportingApplicable} classification flag AND the
 * transaction actually being posted ({@code status == COMPLETED}, this
 * codebase's "POSTED"). Never inferred from {@code taxAmount > 0} or
 * {@code transactionType == SALE} alone, since a Kacchi transaction
 * (SALE_CHALLAN/PURCHASE_CHALLAN) still calculates GST in full but must
 * never appear in GST reporting, and a DRAFT transaction — Kacchi or not —
 * has no accounting/stock effects yet and so must never appear either.
 */
public final class GstReportingEligibility {

    private GstReportingEligibility() {
    }

    public static boolean isEligibleForGstReporting(Sale sale) {
        return Boolean.TRUE.equals(sale.getGstReportingApplicable())
                && sale.getStatus() == SaleStatus.COMPLETED;
    }

    public static boolean isEligibleForGstReporting(Purchase purchase) {
        return Boolean.TRUE.equals(purchase.getGstReportingApplicable())
                && purchase.getStatus() == PurchaseStatus.COMPLETED;
    }

    /**
     * Same rule, for a Credit Note (Phase 5): {@code gstReportingApplicable} is copied
     * verbatim from the source Sale at creation time (never re-derived here), so a note
     * against a Kacchi sale stays excluded exactly like its source.
     */
    public static boolean isEligibleForGstReporting(CreditNote note) {
        return Boolean.TRUE.equals(note.getGstReportingApplicable()) && note.getStatus() == NoteStatus.POSTED;
    }

    public static boolean isEligibleForGstReporting(DebitNote note) {
        return Boolean.TRUE.equals(note.getGstReportingApplicable()) && note.getStatus() == NoteStatus.POSTED;
    }
}
