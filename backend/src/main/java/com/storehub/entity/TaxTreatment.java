package com.storehub.entity;

/**
 * An Item Master item's GST tax treatment (GST & Tax Complete spec sections
 * 15/16) — kept separate from the item's numeric {@code tax} percent so a
 * TAXABLE item's configured rate survives even while it's temporarily
 * marked EXEMPT/NIL_RATED/ZERO_RATED (switching it back to TAXABLE later
 * does not require re-entering the rate). Only TAXABLE items are charged
 * GST at their configured rate; the other three always compute to 0% GST
 * regardless of what {@code tax} holds, but remain distinct categories for
 * reporting (an EXEMPT supply is not the same GST return category as a
 * ZERO_RATED export, even though both currently charge 0 rupees of tax).
 */
public enum TaxTreatment {
    TAXABLE,
    EXEMPT,
    NIL_RATED,
    ZERO_RATED
}
