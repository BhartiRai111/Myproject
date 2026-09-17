package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stock is store-specific (Multi-Store spec sections 7-9): one row per (product, store) —
 * the same Item Master can hold different quantities at different stores. There is
 * deliberately no more a single row per product; {@code product} alone is no longer
 * unique, only the (product, store) pair is. A global/aggregate stock figure is always
 * derived by summing across a product's rows, never stored directly (spec section 15).
 */
@Entity
@Table(name = "inventory", uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product_store", columnNames = {"product_id", "store_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Nullable only at the schema level, to let this column be added safely onto a
     * database that already has Inventory rows from before Multi-Store (spec section
     * 61's staged migration — add the column, then backfill, without a risky NOT NULL
     * ALTER over existing data). Application code always sets it: every new row is
     * created only via {@code InventoryService.getOrCreateInventory}, which requires a
     * store, and the startup migration backfills any pre-existing row left null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.currentStock == null) {
            this.currentStock = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
