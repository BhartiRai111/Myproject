package com.storehub.entity;

import jakarta.persistence.*;

/**
 * The single counter row {@code StoreCodeGeneratorService} increments under a
 * pessimistic row lock — mirrors {@code EmployeeCodeSequence} exactly, so two
 * concurrent "Generate Code" requests can never receive the same store code.
 */
@Entity
@Table(name = "store_code_sequence")
public class StoreCodeSequence {

    /** Always 1 — a single global counter. */
    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected StoreCodeSequence() {
    }

    public StoreCodeSequence(Long id, long lastNumber) {
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
