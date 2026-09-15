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
import java.util.ArrayList;
import java.util.List;

/** A Purchase Debit Note (Phase 5) — the Purchase-side mirror of {@link CreditNote}; see its Javadoc. */
@Entity
@Table(name = "debit_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_number", unique = true, length = 30)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 30)
    private DebitNoteType noteType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_purchase_id", nullable = false)
    private Purchase sourcePurchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(name = "financial_year_id")
    private Long financialYearId;

    @Column(length = 255)
    private String reason;

    @Column(name = "place_of_supply", length = 100)
    private String placeOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", length = 20)
    private GstType gstType;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_impact", nullable = false, length = 20)
    private StockImpactType stockImpact;

    @Column(name = "gst_reporting_applicable")
    private Boolean gstReportingApplicable;

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

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NoteStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Builder.Default
    @OneToMany(mappedBy = "debitNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DebitNoteItem> items = new ArrayList<>();

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "posted_by", length = 150)
    private String postedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "cancelled_by", length = 150)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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
            this.status = NoteStatus.DRAFT;
        }
        if (this.stockImpact == null) {
            this.stockImpact = StockImpactType.FINANCIAL_ADJUSTMENT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(DebitNoteItem item) {
        items.add(item);
        item.setDebitNote(this);
    }
}
