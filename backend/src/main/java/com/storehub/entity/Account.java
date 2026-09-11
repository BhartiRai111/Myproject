package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Chart of Accounts entry (a GL / ledger account). Business logic must
 * always resolve accounts through {@code AccountService} by a well-known
 * code (see {@code SystemAccountCode}) rather than hard-coding an id.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 40)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_group_id")
    private AccountGroup accountGroup;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "opening_balance_type", nullable = false, columnDefinition = "VARCHAR(10)")
    private LedgerEntryType openingBalanceType;

    /** True for the fixed set of accounts the application posts to by code (Cash, Sales, Output CGST, ...). */
    @Builder.Default
    @Column(name = "system_account", nullable = false)
    private boolean systemAccount = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_by", length = 150)
    private String modifiedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.openingBalance == null) {
            this.openingBalance = BigDecimal.ZERO;
        }
        if (this.openingBalanceType == null) {
            this.openingBalanceType = LedgerEntryType.DEBIT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
