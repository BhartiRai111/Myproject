package com.storehub.service;

import com.storehub.entity.FinancialYear;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherSequence;
import com.storehub.repository.VoucherSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * The ONLY place a voucher number is ever generated (Phase 5 spec section
 * 4) — every document service (Sale/Purchase/SalesOrder/PurchaseOrder/
 * Receipt/Payment/CreditNote/DebitNote) calls {@link #next} instead of
 * formatting its own "%s-%06d" string from the entity's auto-increment id.
 * Format: {@code PREFIX/FY-CODE/000001}, e.g. {@code SALE/26-27/000001}.
 *
 * <p>Concurrency: runs in its own {@code REQUIRES_NEW} transaction so the
 * counter row is locked (and released) independently of whatever larger
 * transaction the caller is inside — a long-running Sale-posting
 * transaction never holds the numbering lock, and a numbering failure
 * never poisons the caller's transaction. The row is fetched with
 * {@code SELECT ... FOR UPDATE} ({@link VoucherSequenceRepository#lockForUpdate}),
 * serializing concurrent increments for the same (docType, FY) pair so two
 * requests can never receive the same number.
 *
 * <p>Draft numbering policy (documented, not left implicit): a number is
 * allocated at document CREATION time regardless of draft/posted status —
 * this matches the pre-Phase-5 behavior (Sale/Purchase already assigned
 * their number immediately on save, before the draft/complete branch) and
 * means a draft that is later cancelled or never posted still permanently
 * consumes its number; it is never reused, matching "never reuse a
 * cancelled voucher's number."
 */
@Service
@RequiredArgsConstructor
public class VoucherNumberService {

    private final VoucherSequenceRepository voucherSequenceRepository;
    private final FinancialYearService financialYearService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(VoucherDocType docType, LocalDate transactionDate) {
        FinancialYear fy = financialYearService.resolveForDate(transactionDate);

        voucherSequenceRepository.ensureRowExists(docType.name(), fy.getId());
        VoucherSequence seq = voucherSequenceRepository.lockForUpdate(docType, fy.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Voucher sequence row missing for " + docType + " / FY " + fy.getCode() + " after ensureRowExists"));

        seq.setLastNumber(seq.getLastNumber() + 1);
        voucherSequenceRepository.save(seq);

        return docType.getPrefix() + "/" + fy.getCode() + "/" + String.format("%06d", seq.getLastNumber());
    }
}
