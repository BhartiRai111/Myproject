package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A double-entry accounting voucher. A journal is never physically deleted
 * once POSTED; cancelling its source transaction posts a separate reversal
 * JournalHeader (linked via {@code reversalOfJournalId}) that offsets it,
 * and this header's own status moves to REVERSED for audit visibility.
 */
@Entity
@Table(name = "journal_headers", indexes = {
        @Index(name = "idx_journal_headers_date", columnList = "journal_date"),
        @Index(name = "idx_journal_headers_voucher", columnList = "voucher_type, voucher_id"),
        @Index(name = "idx_journal_headers_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_number", unique = true, length = 30)
    private String journalNumber;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private VoucherType voucherType;

    /** Id of the source Sale/Purchase/Receipt/Payment row (null for a manual JOURNAL voucher). */
    @Column(name = "voucher_id")
    private Long voucherId;

    /** The store of the source voucher, when it has one (Multi-Store spec section 27) — null for a manual JOURNAL voucher. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "voucher_number", length = 30)
    private String voucherNumber;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private JournalStatus status;

    /** Set when this journal is itself a reversal, pointing back at the journal it offsets. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_journal_id")
    private JournalHeader reversalOfJournal;

    @Column(name = "posted_by", length = 150)
    private String postedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JournalDetail> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = JournalStatus.DRAFT;
        }
    }

    public void addLine(JournalDetail line) {
        lines.add(line);
        line.setJournal(this);
    }
}
