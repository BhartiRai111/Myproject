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
 * A store-to-store stock transfer (Multi-Store spec section 20):
 * DRAFT -&gt; APPROVED -&gt; DISPATCHED -&gt; RECEIVED, or CANCELLED from DRAFT/APPROVED.
 * {@code fromStore}/{@code toStore} are fixed at creation, never mutated on a later
 * status transition — matches the "store is fixed at creation" convention every other
 * store-attributed voucher (Sale, Purchase, ...) already follows.
 */
@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_number", unique = true, length = 30)
    private String transferNumber;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_store_id", nullable = false)
    private Store fromStore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_store_id", nullable = false)
    private Store toStore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(15)")
    private StockTransferStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "financial_year_id")
    private Long financialYearId;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "dispatched_by", length = 150)
    private String dispatchedBy;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "received_by", length = 150)
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "cancelled_by", length = 150)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Builder.Default
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StockTransferItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = StockTransferStatus.DRAFT;
        }
    }

    public void addItem(StockTransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }
}
