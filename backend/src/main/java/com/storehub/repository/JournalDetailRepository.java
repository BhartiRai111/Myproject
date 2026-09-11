package com.storehub.repository;

import com.storehub.entity.JournalDetail;
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
            "COALESCE(SUM(d.debitAmount), 0), COALESCE(SUM(d.creditAmount), 0) " +
            "FROM JournalDetail d JOIN d.journal j " +
            "WHERE j.status IN (com.storehub.entity.JournalStatus.POSTED, com.storehub.entity.JournalStatus.REVERSED) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "GROUP BY j.id, j.journalDate, j.journalNumber, j.voucherType, j.voucherNumber, j.narration " +
            "ORDER BY j.journalDate ASC, j.id ASC")
    List<Object[]> dayBookRows(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
