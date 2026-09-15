package com.storehub.entity;

import jakarta.persistence.*;

/**
 * The counter row {@code VoucherNumberService} increments under a
 * pessimistic row lock, one per (docType, financialYear) pair. This is the
 * only place a voucher number is ever generated — no controller/service is
 * allowed to format its own number independently (Phase 5 spec section 4).
 */
@Entity
@Table(name = "voucher_sequences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_voucher_sequence", columnNames = {"doc_type", "financial_year_id"})
})
public class VoucherSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private VoucherDocType docType;

    @Column(name = "financial_year_id", nullable = false)
    private Long financialYearId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected VoucherSequence() {
    }

    public VoucherSequence(VoucherDocType docType, Long financialYearId, long lastNumber) {
        this.docType = docType;
        this.financialYearId = financialYearId;
        this.lastNumber = lastNumber;
    }

    public Long getId() {
        return id;
    }

    public VoucherDocType getDocType() {
        return docType;
    }

    public Long getFinancialYearId() {
        return financialYearId;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(long lastNumber) {
        this.lastNumber = lastNumber;
    }
}
