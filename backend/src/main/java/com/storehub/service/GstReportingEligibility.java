package com.storehub.service;

import com.storehub.entity.Purchase;
import com.storehub.entity.Sale;

/**
 * The single source of truth for "does this transaction belong in GST return
 * reporting" (GSTR-1/GSTR-3B, built in Phase 3). Deliberately reads only the
 * explicit {@code gstReportingApplicable} classification flag set at
 * creation time — never {@code taxAmount > 0} or {@code transactionType ==
 * SALE} alone, since a Kacchi transaction (SALE_CHALLAN/PURCHASE_CHALLAN)
 * still calculates GST in full but must never appear in GST reporting.
 */
public final class GstReportingEligibility {

    private GstReportingEligibility() {
    }

    public static boolean isEligibleForGstReporting(Sale sale) {
        return Boolean.TRUE.equals(sale.getGstReportingApplicable());
    }

    public static boolean isEligibleForGstReporting(Purchase purchase) {
        return Boolean.TRUE.equals(purchase.getGstReportingApplicable());
    }
}
