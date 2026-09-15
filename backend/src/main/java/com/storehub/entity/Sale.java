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

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true, length = 30)
    private String invoiceNumber;

    /**
     * Idempotency key (Phase 6 spec section 39): an optional caller-supplied token — the
     * POS screen generates one per cart before the first submit attempt and resends the
     * SAME token on any retry (double-click, refresh, network timeout). A unique DB
     * constraint means a retry can never create a second Sale; {@code SaleService.createSale}
     * looks this up first and returns the original Sale instead of re-posting.
     */
    @Column(name = "client_request_id", unique = true, length = 100)
    private String clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private SaleStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** SALE (normal GST/Non-GST bill) or SALE_CHALLAN (Kacchi Sale). Fixed at creation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", columnDefinition = "VARCHAR(20)")
    private TransactionType transactionType;

    /**
     * Whether this sale is eligible for GST return reporting (GSTR-1/GSTR-3B, Phase 3).
     * Independent of whether GST was calculated: a Kacchi Sale still calculates GST in
     * full (see cgstAmount/sgstAmount/igstAmount below) but is excluded from reporting.
     */
    @Column(name = "gst_reporting_applicable")
    private Boolean gstReportingApplicable;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", length = 20)
    private GstType gstType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", length = 20)
    private TaxMode taxMode;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_gstin", length = 20)
    private String customerGstin;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 20)
    private PaymentMode paymentMode;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "due_amount", precision = 12, scale = 2)
    private BigDecimal dueAmount;

    @Column(name = "taxable_amount", precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_amount", precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "igst_amount", precision = 12, scale = 2)
    private BigDecimal igstAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Builder.Default
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SaleItem> items = new ArrayList<>();

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
            this.status = SaleStatus.PENDING;
        }
        if (this.transactionType == null) {
            this.transactionType = TransactionType.SALE;
        }
        if (this.gstReportingApplicable == null) {
            this.gstReportingApplicable = this.transactionType != TransactionType.SALE_CHALLAN;
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
        if (this.gstType == null) {
            this.gstType = GstType.NON_GST;
        }
        if (this.paidAmount == null) {
            this.paidAmount = BigDecimal.ZERO;
        }
        if (this.dueAmount == null) {
            this.dueAmount = this.totalAmount;
        }
        if (this.taxableAmount == null) {
            this.taxableAmount = this.totalAmount;
        }
        if (this.cgstAmount == null) {
            this.cgstAmount = BigDecimal.ZERO;
        }
        if (this.sgstAmount == null) {
            this.sgstAmount = BigDecimal.ZERO;
        }
        if (this.igstAmount == null) {
            this.igstAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }

    public void clearItems() {
        items.forEach(item -> item.setSale(null));
        items.clear();
    }
}
