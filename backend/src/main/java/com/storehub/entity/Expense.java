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
 * Operational business expense (Phase 6 spec sections 10-11) — rent,
 * electricity, transport, etc. Posts through the same centralized
 * {@code AccountingService.postJournal}/{@code reverseJournal} every other
 * voucher uses; never a manual P&L edit. GST is optional per expense and,
 * when present, is split CGST/SGST or IGST using the same formula
 * Sale/Purchase already use. {@code itcEligible} is an explicit flag set by
 * the user, never inferred — an expense's GST is not automatically assumed
 * recoverable.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_number", unique = true, length = 30)
    private String expenseNumber;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, length = 100)
    private String category;

    /** Optional master-backed category (spec: proper Expense Category master); null on legacy free-text-category rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_category_id")
    private ExpenseCategory expenseCategory;

    @Column(name = "vendor_name", length = 150)
    private String vendorName;

    /** Optional party for a credit expense (payable, settled later via the existing Payment module) — reuses Supplier, never a new vendor master. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "financial_year_id")
    private Long financialYearId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, columnDefinition = "VARCHAR(20)")
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", columnDefinition = "VARCHAR(20)")
    private TaxMode taxMode;

    @Column(name = "gst_percent", precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(name = "itc_eligible", nullable = false)
    @Builder.Default
    private boolean itcEligible = false;

    /** Purely informational: amount subtracted from the gross figure by the caller before arriving at {@link #taxableAmount} (Gross - Discount = Taxable). */
    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "igst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_number", length = 60)
    private String referenceNumber;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    /** Cash/bank expense: full total paid immediately. Credit (party) expense: 0 until settled via the Payment module. */
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", columnDefinition = "VARCHAR(10)")
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private ExpenseStatus status;

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
            this.status = ExpenseStatus.DRAFT;
        }
        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.paidAmount == null) {
            this.paidAmount = BigDecimal.ZERO;
        }
        if (this.payableAmount == null) {
            this.payableAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
