package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The normalized GST reporting dataset (Phase 3). One row per GST-reportable
 * POSTED Sale/Purchase — created only when {@code gstReportingApplicable}
 * was true on the source transaction at posting time. Never recalculates
 * tax: every amount is copied verbatim from the source transaction, which
 * remains the single source of truth (see Sale/Purchase, GstEntry,
 * PurchaseGstEntry). A cancelled source transaction flips this row's status
 * to REVERSED in place rather than deleting it, so GST reports (which only
 * read ACTIVE rows) stop counting it while the record stays auditable.
 * Unique on (sourceTransactionType, sourceTransactionId) so re-syncing the
 * same source transaction is idempotent and can never create a duplicate.
 */
@Entity
@Table(name = "gst_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gst_transaction_source", columnNames = {"source_transaction_type", "source_transaction_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GstTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SALE or PURCHASE — the Sale.id / Purchase.id this row was synced from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_transaction_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private VoucherType sourceTransactionType;

    @Column(name = "source_transaction_id", nullable = false)
    private Long sourceTransactionId;

    /** The store of the source voucher, when it has one (Multi-Store spec section 27) — null for pre-migration data. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "voucher_number", length = 30)
    private String voucherNumber;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", columnDefinition = "VARCHAR(10)")
    private AccountingPartyType partyType;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "party_name", length = 150)
    private String partyName;

    @Column(name = "party_gstin", length = 20)
    private String partyGstin;

    /** First 2 digits of the party GSTIN (the GST state code); null when no valid GSTIN is on record. */
    @Column(name = "place_of_supply_state_code", length = 2)
    private String placeOfSupplyStateCode;

    /** True (B2B) when partyGstin is a syntactically valid GSTIN; false (B2C) otherwise. */
    @Column(name = "b2b", nullable = false)
    private boolean b2b;

    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "igst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmount;

    @Column(name = "total_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "total_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalValue;

    /** Return period the voucher date falls into, as "YYYY-MM" (e.g. "2026-09"). */
    @Column(name = "return_period", nullable = false, length = 7)
    private String returnPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private GstTransactionStatus status;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = GstTransactionStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
