package com.storehub.entity;

import jakarta.persistence.*;

/**
 * The single counter row {@code BarcodeGeneratorService} increments under a
 * pessimistic row lock — mirrors {@link ItemSkuSequence}'s pattern exactly,
 * kept as a separate counter so internal barcode numbers and SKU numbers
 * never share (and thus never confuse) the same sequence.
 */
@Entity
@Table(name = "item_barcode_sequence")
public class ItemBarcodeSequence {

    /** Always 1 — a single global counter. */
    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected ItemBarcodeSequence() {
    }

    public ItemBarcodeSequence(Long id, long lastNumber) {
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
