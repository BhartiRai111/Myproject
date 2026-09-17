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
 * Ad-hoc Cash In / Cash Out entry (Phase 6 spec section 12) — for money that
 * moves through Cash/Bank without a customer/supplier bill behind it (e.g.
 * "other income", petty cash, a cash adjustment). Posts through the same
 * {@code AccountingService.postJournal}/{@code reverseJournal} as every
 * other voucher; the existing Cash Book/Bank Book (which reads straight off
 * the journal) picks these up automatically — no separate balance is kept
 * here.
 */
@Entity
@Table(name = "cash_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_number", unique = true, length = 30)
    private String transactionNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /** Which store's cash/bank this transaction affects (Multi-Store spec section 25) — fixed at creation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private CashTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, columnDefinition = "VARCHAR(20)")
    private PaymentMode paymentMode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "financial_year_id")
    private Long financialYearId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private CashTransactionStatus status;

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
            this.status = CashTransactionStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
