package com.storehub.service;

import com.storehub.dto.GstLiabilityResponse;
import com.storehub.dto.GstReportListResponse;
import com.storehub.dto.GstSummaryTotals;
import com.storehub.dto.GstTransactionRow;
import com.storehub.dto.Gstr1Response;
import com.storehub.dto.Gstr3bResponse;
import com.storehub.dto.HsnSummaryReportResponse;
import com.storehub.dto.HsnSummaryRow;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.PurchaseGstReportResponse;
import com.storehub.dto.ReconciliationResponse;
import com.storehub.dto.ReconciliationRow;
import com.storehub.dto.TaxRateSummaryReportResponse;
import com.storehub.dto.TaxRateSummaryRow;
import com.storehub.entity.GstTransaction;
import com.storehub.entity.VoucherType;
import com.storehub.repository.GstTransactionRepository;
import com.storehub.repository.PurchaseItemRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SaleItemRepository;
import com.storehub.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The GST Reporting Engine (Phase 3). Every method here reads ONLY from the
 * {@link GstTransaction} dataset (already scoped to POSTED + gstReportingApplicable
 * at sync time — see {@link GstTransactionSyncService}) or from SaleItem/PurchaseItem
 * with the same explicit scoping — never re-derives eligibility from tax amount or
 * transaction type. GST TAX CALCULATION (done at billing time, in full, on every
 * transaction including Kacchi) is not the same thing as GST RETURN REPORTING (what
 * this service produces): a Kacchi transaction never has a GstTransaction row, so it
 * is structurally impossible for it to appear in any report below.
 *
 * Every report here is a reporting/preparation aid built from this app's own data —
 * none of it is an actual government (GSTN) filing integration.
 */
@Service
@RequiredArgsConstructor
public class GstReportingService {

    private static final String GSTR3B_NOTE = "This is a GST return preparation aid generated from StoreHub's own "
            + "records. It is not a government (GSTN) filing integration — nothing here is submitted to the GST portal.";

    private final GstTransactionRepository gstTransactionRepository;
    private final SaleItemRepository saleItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public Gstr1Response gstr1(LocalDate fromDate, LocalDate toDate, String returnPeriod) {
        List<GstTransaction> transactions = gstTransactionRepository.findActiveForSummary(VoucherType.SALE, returnPeriod, fromDate, toDate);

        List<GstTransactionRow> b2b = new ArrayList<>();
        List<GstTransactionRow> b2c = new ArrayList<>();
        for (GstTransaction txn : transactions) {
            GstTransactionRow row = toRow(txn, null);
            if (txn.isB2b()) {
                b2b.add(row);
            } else {
                b2c.add(row);
            }
        }

        List<HsnSummaryRow> hsnSummary = mapHsnRows(saleItemRepository.hsnSummary(fromDate, toDate));
        GstSummaryTotals totals = aggregate(gstTransactionRepository.aggregateTotals(VoucherType.SALE, returnPeriod, fromDate, toDate));

        return Gstr1Response.builder()
                .returnPeriod(returnPeriod)
                .fromDate(fromDate)
                .toDate(toDate)
                .b2bTransactions(b2b)
                .b2cTransactions(b2c)
                .creditNotes(Collections.emptyList())
                .debitNotes(Collections.emptyList())
                .hsnSummary(hsnSummary)
                .totals(totals)
                .build();
    }

    @Transactional(readOnly = true)
    public TaxRateSummaryReportResponse taxRateSummary(LocalDate fromDate, LocalDate toDate) {
        return TaxRateSummaryReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .outward(mapTaxRateRows(saleItemRepository.taxRateSummary(fromDate, toDate)))
                .inward(mapTaxRateRows(purchaseItemRepository.taxRateSummary(fromDate, toDate)))
                .build();
    }

    @Transactional(readOnly = true)
    public HsnSummaryReportResponse hsnSummary(LocalDate fromDate, LocalDate toDate) {
        return HsnSummaryReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .outward(mapHsnRows(saleItemRepository.hsnSummary(fromDate, toDate)))
                .inward(mapHsnRows(purchaseItemRepository.hsnSummary(fromDate, toDate)))
                .build();
    }

    /** Purchase GST Report: every ACTIVE, GST-reportable purchase with its ITC eligibility status. */
    @Transactional(readOnly = true)
    public PurchaseGstReportResponse purchaseGstReport(LocalDate fromDate, LocalDate toDate, String returnPeriod, Pageable pageable) {
        Page<GstTransaction> page = gstTransactionRepository.search(VoucherType.PURCHASE, returnPeriod, fromDate, toDate, null, pageable);
        Page<GstTransactionRow> rows = page.map(txn -> toRow(txn, txn.isB2b()));

        GstSummaryTotals totals = aggregate(gstTransactionRepository.aggregateTotals(VoucherType.PURCHASE, returnPeriod, fromDate, toDate));
        GstSummaryTotals itcTotals = aggregate(gstTransactionRepository.aggregateB2bTotals(VoucherType.PURCHASE, returnPeriod, fromDate, toDate));

        return PurchaseGstReportResponse.builder()
                .returnPeriod(returnPeriod)
                .fromDate(fromDate)
                .toDate(toDate)
                .transactions(PagedResponse.fromPage(rows))
                .totals(totals)
                .eligibleItcTotal(itcTotals.getTotalTax())
                .build();
    }

    @Transactional(readOnly = true)
    public GstReportListResponse outputGstReport(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<GstTransaction> page = gstTransactionRepository.search(VoucherType.SALE, null, fromDate, toDate, null, pageable);
        Page<GstTransactionRow> rows = page.map(txn -> toRow(txn, null));
        GstSummaryTotals totals = aggregate(gstTransactionRepository.aggregateTotals(VoucherType.SALE, null, fromDate, toDate));
        return GstReportListResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .transactions(PagedResponse.fromPage(rows))
                .totals(totals)
                .build();
    }

    /** Input GST report: Purchase AND Expense together (the generic ITC-side view — Purchase GST Report above stays Purchase-only). */
    private static final List<VoucherType> INPUT_GST_TYPES = List.of(VoucherType.PURCHASE, VoucherType.EXPENSE);

    @Transactional(readOnly = true)
    public GstReportListResponse inputGstReport(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<GstTransaction> page = gstTransactionRepository.searchByTypes(INPUT_GST_TYPES, null, fromDate, toDate, pageable);
        Page<GstTransactionRow> rows = page.map(txn -> toRow(txn, txn.isB2b()));
        GstSummaryTotals totals = aggregate(gstTransactionRepository.aggregateTotalsByTypes(INPUT_GST_TYPES, null, fromDate, toDate));
        return GstReportListResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .transactions(PagedResponse.fromPage(rows))
                .totals(totals)
                .build();
    }

    /** GSTR-3B summary for a return period: outward supplies, Input Tax Credit (ITC-eligible Purchase + Expense rows), and net liability. */
    @Transactional(readOnly = true)
    public Gstr3bResponse gstr3bSummary(String returnPeriod) {
        GstSummaryTotals outward = aggregate(gstTransactionRepository.aggregateTotals(VoucherType.SALE, returnPeriod, null, null));
        GstSummaryTotals itc = aggregate(gstTransactionRepository.aggregateB2bTotalsByTypes(INPUT_GST_TYPES, returnPeriod, null, null));
        GstSummaryTotals net = netOf(outward, itc);

        return Gstr3bResponse.builder()
                .returnPeriod(returnPeriod)
                .outwardSupplies(outward)
                .inputTaxCredit(itc)
                .netLiability(net)
                .note(GSTR3B_NOTE)
                .build();
    }

    /** Same underlying math as {@link #gstr3bSummary}, in the output/input/net layout of the GST Liability page. */
    @Transactional(readOnly = true)
    public GstLiabilityResponse gstLiability(String returnPeriod) {
        GstSummaryTotals outward = aggregate(gstTransactionRepository.aggregateTotals(VoucherType.SALE, returnPeriod, null, null));
        GstSummaryTotals input = aggregate(gstTransactionRepository.aggregateB2bTotalsByTypes(INPUT_GST_TYPES, returnPeriod, null, null));

        return GstLiabilityResponse.builder()
                .returnPeriod(returnPeriod)
                .outputCgst(outward.getCgstAmount())
                .outputSgst(outward.getSgstAmount())
                .outputIgst(outward.getIgstAmount())
                .outputTotal(outward.getTotalTax())
                .inputCgst(input.getCgstAmount())
                .inputSgst(input.getSgstAmount())
                .inputIgst(input.getIgstAmount())
                .inputTotal(input.getTotalTax())
                .netCgst(outward.getCgstAmount().subtract(input.getCgstAmount()))
                .netSgst(outward.getSgstAmount().subtract(input.getSgstAmount()))
                .netIgst(outward.getIgstAmount().subtract(input.getIgstAmount()))
                .netTotal(outward.getTotalTax().subtract(input.getTotalTax()))
                .build();
    }

    /**
     * Compares every eligible (POSTED + gstReportingApplicable) Sale/Purchase in a date range against the
     * GstTransaction dataset it should have synced to. MATCHED/MISMATCHED/MISSING are derived per source
     * transaction; DUPLICATE is a defensive check only — the DB unique constraint on
     * (source_transaction_type, source_transaction_id) makes a true duplicate row impossible in practice.
     */
    @Transactional(readOnly = true)
    public ReconciliationResponse reconciliation(LocalDate fromDate, LocalDate toDate) {
        List<ReconciliationRow> rows = new ArrayList<>();
        long matched = 0;
        long mismatched = 0;
        long missing = 0;

        matched += reconcileSide(VoucherType.SALE, saleRepository.findEligibleForReconciliation(fromDate, toDate), rows);
        mismatched += rows.stream().filter(r -> "MISMATCHED".equals(r.getStatus()) && r.getSourceTransactionType() == VoucherType.SALE).count();
        missing += rows.stream().filter(r -> "MISSING".equals(r.getStatus()) && r.getSourceTransactionType() == VoucherType.SALE).count();

        int beforePurchase = rows.size();
        reconcileSide(VoucherType.PURCHASE, purchaseRepository.findEligibleForReconciliation(fromDate, toDate), rows);
        for (int i = beforePurchase; i < rows.size(); i++) {
            ReconciliationRow r = rows.get(i);
            if ("MATCHED".equals(r.getStatus())) {
                matched++;
            } else if ("MISMATCHED".equals(r.getStatus())) {
                mismatched++;
            } else if ("MISSING".equals(r.getStatus())) {
                missing++;
            }
        }

        long duplicates = countDuplicates(VoucherType.SALE) + countDuplicates(VoucherType.PURCHASE);

        return ReconciliationResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .rows(rows)
                .matchedCount(matched)
                .mismatchedCount(mismatched)
                .missingCount(missing)
                .duplicateCount(duplicates)
                .build();
    }

    private long reconcileSide(VoucherType type, List<Object[]> eligibleSources, List<ReconciliationRow> rows) {
        Map<Long, GstTransaction> activeById = new HashMap<>();
        for (GstTransaction txn : gstTransactionRepository.findBySourceTransactionTypeAndStatus(type, com.storehub.entity.GstTransactionStatus.ACTIVE)) {
            activeById.put(txn.getSourceTransactionId(), txn);
        }

        long matchedCount = 0;
        for (Object[] source : eligibleSources) {
            Long id = (Long) source[0];
            String voucherNumber = (String) source[1];
            LocalDate voucherDate = (LocalDate) source[2];
            BigDecimal taxableAmount = (BigDecimal) source[3];
            BigDecimal cgst = (BigDecimal) source[4];
            BigDecimal sgst = (BigDecimal) source[5];
            BigDecimal igst = (BigDecimal) source[6];
            BigDecimal totalTax = cgst.add(sgst).add(igst);

            GstTransaction reported = activeById.get(id);
            if (reported == null) {
                rows.add(ReconciliationRow.builder()
                        .sourceTransactionType(type)
                        .sourceTransactionId(id)
                        .voucherNumber(voucherNumber)
                        .voucherDate(voucherDate)
                        .status("MISSING")
                        .sourceTaxableAmount(taxableAmount)
                        .reportedTaxableAmount(null)
                        .sourceTotalTax(totalTax)
                        .reportedTotalTax(null)
                        .remarks("Eligible for GST reporting but no active GstTransaction row was found — re-sync needed")
                        .build());
                continue;
            }

            boolean amountsMatch = taxableAmount.compareTo(reported.getTaxableAmount()) == 0
                    && totalTax.compareTo(reported.getTotalTax()) == 0;
            if (amountsMatch) {
                matchedCount++;
                rows.add(ReconciliationRow.builder()
                        .sourceTransactionType(type)
                        .sourceTransactionId(id)
                        .voucherNumber(voucherNumber)
                        .voucherDate(voucherDate)
                        .status("MATCHED")
                        .sourceTaxableAmount(taxableAmount)
                        .reportedTaxableAmount(reported.getTaxableAmount())
                        .sourceTotalTax(totalTax)
                        .reportedTotalTax(reported.getTotalTax())
                        .remarks(null)
                        .build());
            } else {
                rows.add(ReconciliationRow.builder()
                        .sourceTransactionType(type)
                        .sourceTransactionId(id)
                        .voucherNumber(voucherNumber)
                        .voucherDate(voucherDate)
                        .status("MISMATCHED")
                        .sourceTaxableAmount(taxableAmount)
                        .reportedTaxableAmount(reported.getTaxableAmount())
                        .sourceTotalTax(totalTax)
                        .reportedTotalTax(reported.getTotalTax())
                        .remarks("Amounts on the source transaction differ from the synced GST reporting row")
                        .build());
            }
        }
        return matchedCount;
    }

    private long countDuplicates(VoucherType type) {
        long count = 0;
        for (Object[] row : gstTransactionRepository.countActiveBySource(type)) {
            long occurrences = (long) row[1];
            if (occurrences > 1) {
                count++;
            }
        }
        return count;
    }

    private GstTransactionRow toRow(GstTransaction txn, Boolean itcEligible) {
        return GstTransactionRow.builder()
                .gstTransactionId(txn.getId())
                .sourceTransactionType(txn.getSourceTransactionType())
                .sourceTransactionId(txn.getSourceTransactionId())
                .voucherNumber(txn.getVoucherNumber())
                .voucherDate(txn.getVoucherDate())
                .partyType(txn.getPartyType())
                .partyId(txn.getPartyId())
                .partyName(txn.getPartyName())
                .partyGstin(txn.getPartyGstin())
                .placeOfSupplyStateCode(txn.getPlaceOfSupplyStateCode())
                .b2b(txn.isB2b())
                .itcEligible(itcEligible)
                .taxableAmount(txn.getTaxableAmount())
                .cgstAmount(txn.getCgstAmount())
                .sgstAmount(txn.getSgstAmount())
                .igstAmount(txn.getIgstAmount())
                .totalTax(txn.getTotalTax())
                .totalValue(txn.getTotalValue())
                .returnPeriod(txn.getReturnPeriod())
                .build();
    }

    private List<HsnSummaryRow> mapHsnRows(List<Object[]> raw) {
        List<HsnSummaryRow> rows = new ArrayList<>();
        for (Object[] r : raw) {
            rows.add(HsnSummaryRow.builder()
                    .hsnCode((String) r[0])
                    .description((String) r[1])
                    .unit((String) r[2])
                    .totalQuantity(BigDecimal.valueOf(((Number) r[3]).longValue()))
                    .taxableAmount((BigDecimal) r[4])
                    .cgstAmount((BigDecimal) r[5])
                    .sgstAmount((BigDecimal) r[6])
                    .igstAmount((BigDecimal) r[7])
                    .totalValue((BigDecimal) r[8])
                    .build());
        }
        return rows;
    }

    private List<TaxRateSummaryRow> mapTaxRateRows(List<Object[]> raw) {
        List<TaxRateSummaryRow> rows = new ArrayList<>();
        for (Object[] r : raw) {
            rows.add(TaxRateSummaryRow.builder()
                    .gstPercent((BigDecimal) r[0])
                    .taxableAmount((BigDecimal) r[1])
                    .cgstAmount((BigDecimal) r[2])
                    .sgstAmount((BigDecimal) r[3])
                    .igstAmount((BigDecimal) r[4])
                    .totalValue((BigDecimal) r[5])
                    .build());
        }
        return rows;
    }

    private GstSummaryTotals aggregate(List<Object[]> raw) {
        if (raw.isEmpty()) {
            return GstSummaryTotals.zero();
        }
        Object[] r = raw.get(0);
        return GstSummaryTotals.builder()
                .taxableAmount((BigDecimal) r[0])
                .cgstAmount((BigDecimal) r[1])
                .sgstAmount((BigDecimal) r[2])
                .igstAmount((BigDecimal) r[3])
                .totalTax((BigDecimal) r[4])
                .totalValue((BigDecimal) r[5])
                .transactionCount((long) r[6])
                .build();
    }

    private GstSummaryTotals netOf(GstSummaryTotals outward, GstSummaryTotals itc) {
        return GstSummaryTotals.builder()
                .taxableAmount(outward.getTaxableAmount().subtract(itc.getTaxableAmount()))
                .cgstAmount(outward.getCgstAmount().subtract(itc.getCgstAmount()))
                .sgstAmount(outward.getSgstAmount().subtract(itc.getSgstAmount()))
                .igstAmount(outward.getIgstAmount().subtract(itc.getIgstAmount()))
                .totalTax(outward.getTotalTax().subtract(itc.getTotalTax()))
                .totalValue(outward.getTotalValue().subtract(itc.getTotalValue()))
                .transactionCount(outward.getTransactionCount())
                .build();
    }
}
