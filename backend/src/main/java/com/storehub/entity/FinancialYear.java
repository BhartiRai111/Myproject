package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An Indian financial year (1-Apr to 31-Mar), e.g. code "26-27" for
 * 01-Apr-2026 to 31-Mar-2027. This is the sole source of truth for "which FY
 * does this date belong to" — {@link com.storehub.service.FinancialYearService}
 * resolves every transaction date against these rows rather than
 * recomputing the Apr-Mar rule inline (see also
 * {@link com.storehub.util.FinancialYearUtil}, which only derives the
 * calendar boundaries and is used to auto-seed this row on startup; it does
 * not know whether a year is OPEN/CLOSED/current). Historical transactions
 * are never re-tagged when the current FY changes: their journalDate/
 * saleDate/etc. already fixes which FY they fall in, computed on demand.
 */
@Entity
@Table(name = "financial_years", uniqueConstraints = {
        @UniqueConstraint(name = "uk_financial_year_code", columnNames = {"code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name, e.g. "FY 2026-27". */
    @Column(nullable = false, length = 30)
    private String name;

    /** Short code used in voucher numbers, e.g. "26-27". */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FinancialYearStatus status;

    /** At most one row may have this true — enforced in FinancialYearService, not the DB, since flipping it is a multi-row update. */
    @Column(name = "is_current", nullable = false)
    private boolean current;

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
            this.status = FinancialYearStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
