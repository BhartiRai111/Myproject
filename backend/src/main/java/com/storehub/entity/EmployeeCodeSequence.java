package com.storehub.entity;

import jakarta.persistence.*;

/**
 * The single counter row {@code EmployeeCodeGeneratorService} increments under a
 * pessimistic row lock — mirrors {@code ItemSkuSequence}'s pattern exactly, so two
 * concurrent "Generate Code" requests can never receive the same employee code.
 */
@Entity
@Table(name = "employee_code_sequence")
public class EmployeeCodeSequence {

    /** Always 1 — a single global counter. */
    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected EmployeeCodeSequence() {
    }

    public EmployeeCodeSequence(Long id, long lastNumber) {
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
