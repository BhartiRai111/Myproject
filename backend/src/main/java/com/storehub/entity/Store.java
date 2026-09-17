package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A Store/Branch/Warehouse (Multi-Store spec section 4) — the operational location a
 * Sale/Purchase/Inventory row belongs to. Deliberately NOT a new top-level module (spec
 * section 1): lives under Master → Store/Branch. GSTIN/state are optional per-store
 * overrides (spec section 32) — when a store has its own GSTIN, its transactions use it
 * as the seller GST context instead of the business-wide {@link BusinessGstConfig}
 * singleton; when left blank, the store shares the business's single GST registration.
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_code", nullable = false, unique = true, length = 30)
    private String storeCode;

    @Column(name = "store_name", nullable = false, length = 150)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false, length = 20)
    private StoreType storeType;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(length = 10)
    private String pincode;

    @Column(length = 15)
    private String phone;

    @Column(length = 100)
    private String email;

    /** Optional — when set, overrides the business-wide {@link BusinessGstConfig} GSTIN for this store's transactions. */
    @Column(length = 15)
    private String gstin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

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
            this.status = StoreStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
