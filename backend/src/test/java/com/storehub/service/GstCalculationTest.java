package com.storehub.service;

import com.storehub.entity.TaxMode;
import com.storehub.entity.TaxTreatment;
import com.storehub.util.GstinValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GST & Tax Complete spec section 57's tax-math regression cases, tested
 * directly against {@link GstCalculationService} — no Spring context needed
 * since it is a stateless, dependency-free service. This proves the ONE
 * central GST engine's math in isolation; {@link GstCalculationIntegrationTest}
 * proves it is actually wired correctly end-to-end through
 * SaleService/PurchaseService/CreditNoteService/DebitNoteService.
 *
 * <p>Cases 6/7 (Kacchi Sale/Purchase GST-calculated-but-not-reported) are
 * already fully exercised end-to-end by {@link KacchiTransactionTest} and
 * are not duplicated here.
 */
class GstCalculationTest {

    private final GstCalculationService gstCalculationService = new GstCalculationService();

    // ---- Case 1: same-state GST sale, ₹1000 @ 18% -> CGST 90 / SGST 90 / IGST 0 ----
    @Test
    void calculateLine_sameState_splitsCgstAndSgstEqually() {
        GstCalculationService.LineTaxResult result =
                gstCalculationService.calculateLine(new BigDecimal("1000"), new BigDecimal("18"), TaxMode.INTRA_STATE);

        assertThat(result.getGstAmount()).isEqualByComparingTo("180.00");
        assertThat(result.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(result.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(result.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1180.00");
    }

    // ---- Case 2: inter-state GST sale -> CGST 0 / SGST 0 / IGST 180 ----
    @Test
    void calculateLine_interState_appliesFullAmountToIgst() {
        GstCalculationService.LineTaxResult result =
                gstCalculationService.calculateLine(new BigDecimal("1000"), new BigDecimal("18"), TaxMode.INTER_STATE);

        assertThat(result.getCgstAmount()).isEqualByComparingTo("0");
        assertThat(result.getSgstAmount()).isEqualByComparingTo("0");
        assertThat(result.getIgstAmount()).isEqualByComparingTo("180.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1180.00");
    }

    // ---- Case 5: discount is subtracted BEFORE GST — ₹1000 gross - ₹100 discount -> ₹900 taxable -> ₹162 GST @ 18% ----
    @Test
    void calculateLine_discountAlreadyAppliedByCaller_taxesOnlyTheNetAmount() {
        BigDecimal gross = new BigDecimal("1000");
        BigDecimal discount = new BigDecimal("100");
        BigDecimal taxableAmount = gross.subtract(discount);

        GstCalculationService.LineTaxResult result =
                gstCalculationService.calculateLine(taxableAmount, new BigDecimal("18"), TaxMode.INTRA_STATE);

        assertThat(result.getTaxableAmount()).isEqualByComparingTo("900.00");
        assertThat(result.getGstAmount()).isEqualByComparingTo("162.00");
        assertThat(result.getCgstAmount()).isEqualByComparingTo("81.00");
        assertThat(result.getSgstAmount()).isEqualByComparingTo("81.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1062.00");
    }

    // ---- Item tax treatment: EXEMPT/NIL_RATED/ZERO_RATED always compute to 0% GST regardless of the configured rate ----
    @Test
    void calculateLine_nonTaxableTreatment_forcesZeroGstRegardlessOfRate() {
        for (TaxTreatment treatment : List.of(TaxTreatment.EXEMPT, TaxTreatment.NIL_RATED, TaxTreatment.ZERO_RATED)) {
            GstCalculationService.LineTaxResult result = gstCalculationService.calculateLine(
                    new BigDecimal("1000"), new BigDecimal("18"), TaxMode.INTRA_STATE, treatment);

            assertThat(result.getGstAmount()).as("treatment=%s", treatment).isEqualByComparingTo("0");
            assertThat(result.getCgstAmount()).as("treatment=%s", treatment).isEqualByComparingTo("0");
            assertThat(result.getSgstAmount()).as("treatment=%s", treatment).isEqualByComparingTo("0");
            assertThat(result.getTotalAmount()).as("treatment=%s", treatment).isEqualByComparingTo("1000.00");
        }
    }

    @Test
    void calculateLine_taxableTreatment_usesTheConfiguredRate() {
        GstCalculationService.LineTaxResult result = gstCalculationService.calculateLine(
                new BigDecimal("1000"), new BigDecimal("18"), TaxMode.INTRA_STATE, TaxTreatment.TAXABLE);

        assertThat(result.getGstAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void calculateLine_nullTreatment_defaultsToTaxable() {
        GstCalculationService.LineTaxResult result = gstCalculationService.calculateLine(
                new BigDecimal("1000"), new BigDecimal("18"), TaxMode.INTRA_STATE, null);

        assertThat(result.getGstAmount()).isEqualByComparingTo("180.00");
    }

    // ---- suggestTaxMode: a best-effort frontend hint, never a hard server override ----
    @Test
    void suggestTaxMode_sameState_suggestsIntraState() {
        assertThat(gstCalculationService.suggestTaxMode("Maharashtra", "maharashtra")).isEqualTo(TaxMode.INTRA_STATE);
    }

    @Test
    void suggestTaxMode_differentState_suggestsInterState() {
        assertThat(gstCalculationService.suggestTaxMode("Maharashtra", "Gujarat")).isEqualTo(TaxMode.INTER_STATE);
    }

    @Test
    void suggestTaxMode_unknownState_suggestsNothing() {
        assertThat(gstCalculationService.suggestTaxMode(null, "Gujarat")).isNull();
        assertThat(gstCalculationService.suggestTaxMode("Maharashtra", "")).isNull();
    }

    // ---- GSTIN validation ----
    @Test
    void gstinValidator_validGstin_isAccepted() {
        assertThat(GstinValidator.isValid("27AAAPL1234C1Z5")).isTrue();
        assertThat(GstinValidator.extractStateCode("27AAAPL1234C1Z5")).isEqualTo("27");
    }

    @Test
    void gstinValidator_wrongLengthOrShape_isRejected() {
        assertThat(GstinValidator.isValid("27AAAPL1234C1Z")).isFalse(); // too short
        assertThat(GstinValidator.isValid("27AAAPL1234C1Z55")).isFalse(); // too long
        assertThat(GstinValidator.isValid("XXAAAPL1234C1Z5")).isFalse(); // non-numeric state code
        assertThat(GstinValidator.isValid("")).isFalse();
        assertThat(GstinValidator.isValid(null)).isFalse();
    }
}
