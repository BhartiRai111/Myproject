import { TaxMode } from '../types/sale';

/**
 * Best-effort Place of Supply suggestion (GST & Tax Complete spec sections 11/12) —
 * mirrors the backend's GstCalculationService#suggestTaxMode exactly. Returns null
 * when either state is unknown, so the caller's existing tax mode is left untouched.
 * This is a pre-select hint for the form only — the backend never trusts or
 * re-derives tax mode from this; the user's own dropdown choice remains authoritative.
 */
export function suggestTaxMode(sellerState?: string | null, partyState?: string | null): TaxMode | null {
  if (!sellerState || !sellerState.trim() || !partyState || !partyState.trim()) {
    return null;
  }
  return sellerState.trim().toLowerCase() === partyState.trim().toLowerCase() ? 'INTRA_STATE' : 'INTER_STATE';
}
