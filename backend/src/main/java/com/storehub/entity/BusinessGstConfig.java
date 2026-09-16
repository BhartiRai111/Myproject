package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The seller/business's own GST registration details (GST & Tax Complete spec
 * section 13) — a singleton row (always id 1) rather than a list, since a
 * single StoreHub instance represents one registered business. This is the
 * "seller state" anchor {@link com.storehub.service.GstCalculationService#suggestTaxMode}
 * compares against a Party/Customer/Supplier's state to suggest CGST+SGST vs
 * IGST; it never overrides the transaction's own manually-chosen tax mode.
 *
 * <p>State is a reference to the existing {@link State} master (not a
 * separately duplicated stateCode column) so Place of Supply comparisons
 * share one source of truth with Party/Address — the state's own {@code code}
 * is exposed as this business's state code.
 */
@Entity
@Table(name = "business_gst_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessGstConfig {

    @Id
    private Long id;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(length = 15)
    private String gstin;

    @Column(length = 10)
    private String pan;

    @Column(columnDefinition = "TEXT")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;

    @Column(length = 10)
    private String pincode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
