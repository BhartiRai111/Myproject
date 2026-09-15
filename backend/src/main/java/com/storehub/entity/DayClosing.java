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
 * Day Closing record (Phase 6 spec sections 16-17) — one per calendar date.
 * {@code expectedCash} is never computed here: it is read once at close time
 * from {@link com.storehub.service.CashBankBookService#cashBook}, the same
 * Cash Book every other page uses, so it can never drift from the
 * accounting journal. Closing a day is a record of what the operator
 * counted and any explained difference — it never adjusts accounting
 * balances itself; a real cash shortage/overage still needs an explicit,
 * audited Cash Adjustment (a CashTransaction) if the business wants it
 * reflected in the ledger.
 */
@Entity
@Table(name = "day_closings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closing_date", nullable = false, unique = true)
    private LocalDate closingDate;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "actual_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualCash;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(name = "difference_reason", length = 500)
    private String differenceReason;

    @Column(name = "financial_year_id")
    private Long financialYearId;

    @Column(name = "closed_by", length = 150)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
