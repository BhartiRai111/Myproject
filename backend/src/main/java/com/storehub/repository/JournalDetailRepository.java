package com.storehub.repository;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.VoucherType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalDetailRepository extends JpaRepository<JournalDetail, Long> {

    List<JournalDetail> findByJournalIdOrderById(Long journalId);

    @Query("SELECT d FROM JournalDetail d JOIN FETCH d.journal j WHERE d.account.id = :accountId " +
            "AND j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "ORDER BY j.journalDate ASC, j.id ASC, d.id ASC")
    List<JournalDetail> findLedgerLines(@Param("accountId") Long accountId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    @Query("SELECT d.account.id, COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:asOfDate IS NULL OR j.journalDate <= :asOfDate) " +
            "GROUP BY d.account.id")
    List<Object[]> sumDebitCreditByAccount(@Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE d.account.id = :accountId " +
            "AND j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND j.journalDate < :beforeDate")
    List<Object[]> sumDebitCreditBefore(@Param("accountId") Long accountId, @Param("beforeDate") LocalDate beforeDate);

    @Query("SELECT COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE d.account.id = :accountId " +
            "AND j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED)")
    List<Object[]> sumDebitCreditForAccount(@Param("accountId") Long accountId);

    @Query("SELECT j.journalDate, j.id, j.journalNumber, j.voucherType, j.voucherNumber, j.narration, " +
            "COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0), j.status " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY j.id, j.journalDate, j.journalNumber, j.voucherType, j.voucherNumber, j.narration, j.status " +
            "ORDER BY j.journalDate ASC, j.id ASC")
    List<Object[]> dayBookRows(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT j.journalDate, j.id, j.journalNumber, j.voucherType, j.voucherNumber, j.narration, " +
            "COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0), j.status " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:voucherType IS NULL OR j.voucherType = :voucherType) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY j.id, j.journalDate, j.journalNumber, j.voucherType, j.voucherNumber, j.narration, j.status " +
            "ORDER BY j.journalDate ASC, j.id ASC")
    List<Object[]> dayBookRows(@Param("voucherType") VoucherType voucherType,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate);

    /** Per-account debit/credit activity within an inclusive date range (used by Account Summary, P&L, Expense/Income Summary — never "as of", always a period flow). */
    @Query("SELECT d.account.id, COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY d.account.id")
    List<Object[]> sumDebitCreditByAccountRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Ledger lines for one account, grouped per voucher/journal with an account/voucher breakdown (Expense/Income Summary drill rows). */
    @Query("SELECT j.journalDate, j.id, j.journalNumber, j.voucherType, j.voucherNumber, d.narration, j.narration, " +
            "COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE d.account.id = :accountId " +
            "AND j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY j.id, j.journalDate, j.journalNumber, j.voucherType, j.voucherNumber, d.narration, j.narration " +
            "ORDER BY j.journalDate ASC, j.id ASC")
    List<Object[]> accountVoucherRows(@Param("accountId") Long accountId,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    /** Net (debit-credit) per party, for all activity strictly before a date — the "opening balance" for a Party Ledger/Receivable/Payable report. */
    @Query("SELECT d.partyId, COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND d.partyType = :partyType " +
            "AND (:beforeDate IS NULL OR j.journalDate < :beforeDate) " +
            "GROUP BY d.partyId")
    List<Object[]> sumByPartyBefore(@Param("partyType") AccountingPartyType partyType, @Param("beforeDate") LocalDate beforeDate);

    /** Per-party activity within an inclusive date range, split by the source voucher type (SALE/RECEIPT or PURCHASE/PAYMENT) — the Receivable/Payable movement columns. */
    @Query("SELECT d.partyId, d.referenceType, COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND d.partyType = :partyType " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY d.partyId, d.referenceType")
    List<Object[]> sumByPartyAndReferenceTypeRange(@Param("partyType") AccountingPartyType partyType,
                                                     @Param("fromDate") LocalDate fromDate,
                                                     @Param("toDate") LocalDate toDate);

    /** All ledger lines for one party (customer or supplier) — the control-account (Customer Receivable / Supplier Payable) movements tagged to them. */
    @Query("SELECT d FROM JournalDetail d JOIN FETCH d.journal j WHERE d.partyType = :partyType AND d.partyId = :partyId " +
            "AND j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "ORDER BY j.journalDate ASC, j.id ASC, d.id ASC")
    List<JournalDetail> findLedgerLinesByParty(@Param("partyType") AccountingPartyType partyType,
                                                @Param("partyId") Long partyId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

    /** All-time net (debit-credit) per party — used by the Accounting Health Check's ledger-mismatch comparison against CustomerLedgerEntry/SupplierLedgerEntry. */
    @Query("SELECT d.partyId, COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND d.partyType = :partyType " +
            "GROUP BY d.partyId")
    List<Object[]> sumByParty(@Param("partyType") AccountingPartyType partyType);

    /** Detects a journal whose own lines don't balance (debit ≠ credit) — should never happen given AccountingService's posting-time validation; the Health Check verifies it anyway rather than assuming. */
    @Query("SELECT j.id, j.journalNumber, SUM(d.debitAmount), SUM(d.creditAmount) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "GROUP BY j.id, j.journalNumber " +
            "HAVING SUM(d.debitAmount) <> SUM(d.creditAmount)")
    List<Object[]> findUnbalancedJournals();
}
