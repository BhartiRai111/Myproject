package com.storehub.entity;

import jakarta.persistence.*;

/**
 * The single counter row {@code SkuGeneratorService} increments under a
 * pessimistic row lock (mirrors {@link VoucherSequence}'s pattern for
 * voucher numbering) — the only place an auto-generated SKU number is ever
 * produced, so two concurrent "Generate SKU" requests can never receive the
 * same number.
 */
@Entity
@Table(name = "item_sku_sequence")
public class ItemSkuSequence {

    /** Always 1 — a single global counter, unlike VoucherSequence which is per (docType, FY). */
    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected ItemSkuSequence() {
    }

    public ItemSkuSequence(Long id, long lastNumber) {
        this.id = id;
        this.lastNumber = lastNumber;
    }

    public Long getId() {
        return id;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(long lastNumber) {
        this.lastNumber = lastNumber;
    }
}
