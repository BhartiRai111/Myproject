package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One debit-or-credit line of a JournalHeader. Exactly one of
 * debitAmount/creditAmount is non-zero (enforced by AccountingService,
 * not a DB check constraint, since MySQL/Hibernate's ddl-auto=update
 * cannot reliably add CHECK constraints across MySQL versions).
 */
@Entity
@Table(name = "journal_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id", nullable = false)
    private JournalHeader journal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Builder.Default
    @Column(name = "debit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "credit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", columnDefinition = "VARCHAR(10)")
    private AccountingPartyType partyType;

    @Column(name = "party_id")
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", columnDefinition = "VARCHAR(20)")
    private VoucherType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;
}
