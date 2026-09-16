package com.storehub.service;

import com.storehub.entity.TaxMode;
import com.storehub.entity.TaxTreatment;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one central GST calculation engine (GST & Tax Complete spec section 6)
 * — every financial module (Sale, Purchase, Expense; Kacchi variants reuse
 * Sale/Purchase directly) must compute its line-level taxable value, GST
 * amount, and CGST/SGST/IGST split through {@link #calculateLine} instead of
 * re-implementing the same eight lines of {@link BigDecimal} arithmetic.
 * Before this service existed, SaleService, PurchaseService, and
 * ExpenseService each carried an identical, independently-maintained copy of
 * this math — a classic "one central engine" violation the spec explicitly
 * calls out.
 *
 * <p>TAX CALCULATION is deliberately kept separate from GST REPORTING
 * ELIGIBILITY here: this service only ever answers "how much tax", never
 * "does this belong in a GST return" — that question belongs to
 * {@link GstReportingEligibility} alone, so a Kacchi transaction still gets
 * a fully correct CGST/SGST/IGST split from this service even though its
 * result will later be excluded from GSTR-1/GSTR-3B.
 *
 * <p>Discount must already be subtracted from gross by the caller before
 * calling this method — {@code taxableAmount} arrives as gross minus
 * discount, matching the mandated Gross → Discount → Taxable → GST → Total
 * sequence (spec section 17).
 */
@Service
public class GstCalculationService {

    /**
     * @param taxableAmount gross line value minus discount (already computed by the caller)
     * @param gstPercent    the rate to apply — the caller is responsible for zeroing this
     *                      when the parent document is NON_GST or the item's tax treatment
     *                      is EXEMPT/NIL_RATED/ZERO_RATED
     * @param taxMode       INTRA_STATE (CGST+SGST) or INTER_STATE (IGST); ignored if no tax is due
     */
    public LineTaxResult calculateLine(BigDecimal taxableAmount, BigDecimal gstPercent, TaxMode taxMode) {
        return calculateLine(taxableAmount, gstPercent, taxMode, TaxTreatment.TAXABLE);
    }

    /**
     * Item-taxability-aware overload (spec sections 15/16): a non-TAXABLE item
     * (EXEMPT/NIL_RATED/ZERO_RATED) always computes to 0% GST regardless of what
     * rate is configured on it, so a TAXABLE item's rate survives untouched if it's
     * later switched back. {@code taxTreatment == null} is treated as TAXABLE (the
     * entity default) rather than silently zeroing tax for legacy/unset items.
     *
     * @param taxTreatment the item's GST taxability category; null is treated as TAXABLE
     */
    public LineTaxResult calculateLine(BigDecimal taxableAmount, BigDecimal gstPercent, TaxMode taxMode, TaxTreatment taxTreatment) {
        BigDecimal effectivePercent = (taxTreatment == null || taxTreatment == TaxTreatment.TAXABLE) ? nvl(gstPercent) : BigDecimal.ZERO;
        BigDecimal gstAmount = effectivePercent.signum() > 0
                ? taxableAmount.multiply(effectivePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        if (gstAmount.signum() > 0) {
            if (taxMode == TaxMode.INTER_STATE) {
                igst = gstAmount;
            } else {
                cgst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sgst = gstAmount.subtract(cgst);
            }
        }

        return LineTaxResult.builder()
                .taxableAmount(taxableAmount)
                .gstPercent(effectivePercent)
                .gstAmount(gstAmount)
                .cgstAmount(cgst)
                .sgstAmount(sgst)
                .igstAmount(igst)
                .totalAmount(taxableAmount.add(gstAmount))
                .build();
    }

    /**
     * Best-effort Place of Supply suggestion (spec sections 11/12): compares the seller's
     * configured state against the party's recorded state. Returns null when either is
     * unknown, so callers fall back to their existing default rather than guessing — this
     * is a suggestion for the frontend to pre-select, never a server-side override of an
     * explicitly chosen tax mode (real-world edge cases like exports/SEZ legitimately need
     * a human decision, per the spec's own caution against blindly hard-coding this).
     */
    public TaxMode suggestTaxMode(String sellerState, String partyState) {
        if (sellerState == null || sellerState.isBlank() || partyState == null || partyState.isBlank()) {
            return null;
        }
        return sellerState.trim().equalsIgnoreCase(partyState.trim()) ? TaxMode.INTRA_STATE : TaxMode.INTER_STATE;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Getter
    @Builder
    public static class LineTaxResult {
        private BigDecimal taxableAmount;
        private BigDecimal gstPercent;
        private BigDecimal gstAmount;
        private BigDecimal cgstAmount;
        private BigDecimal sgstAmount;
        private BigDecimal igstAmount;
        private BigDecimal totalAmount;
    }
}
