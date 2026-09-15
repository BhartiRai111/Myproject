package com.storehub.repository;

import com.storehub.entity.JournalHeader;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalHeaderRepository extends JpaRepository<JournalHeader, Long> {

    /**
     * "Currently active original posting" lookup/existence checks. Filtered to
     * reversalOfJournal IS NULL so a reversal journal (which shares the same
     * voucherType/voucherId and is itself POSTED) never masquerades as the
     * original when checking for duplicates or finding what to reverse.
     */
    boolean existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
            VoucherType voucherType, Long voucherId, JournalStatus status);

    Optional<JournalHeader> findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(
            VoucherType voucherType, Long voucherId, JournalStatus status);

    @Query("SELECT j FROM JournalHeader j WHERE " +
            "(:voucherType IS NULL OR j.voucherType = :voucherType) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "AND (:search IS NULL OR :search = '' OR " +
            "  LOWER(j.journalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(j.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<JournalHeader> search(@Param("voucherType") VoucherType voucherType,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("search") String search,
                                Pageable pageable);

    @Query("SELECT j FROM JournalHeader j WHERE " +
            "(:voucherType IS NULL OR j.voucherType = :voucherType) " +
            "AND (:status IS NULL OR j.status = :status) " +
            "AND (:fromDate IS NULL OR j.journalDate >= :fromDate) " +
            "AND (:toDate IS NULL OR j.journalDate <= :toDate) " +
            "AND (:search IS NULL OR :search = '' OR " +
            "  LOWER(j.journalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(j.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<JournalHeader> search(@Param("voucherType") VoucherType voucherType,
                                @Param("status") JournalStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("search") String search,
                                Pageable pageable);

    /** Every (voucherType, voucherId) with more than one currently-active (POSTED, non-reversal) journal — should never occur given AccountingService's duplicate-post guard; the Health Check verifies it anyway. */
    @Query("SELECT j.voucherType, j.voucherId, COUNT(j) FROM JournalHeader j " +
            "WHERE j.status = com.storehub.entity.JournalStatus.POSTED AND j.reversalOfJournal IS NULL AND j.voucherId IS NOT NULL " +
            "GROUP BY j.voucherType, j.voucherId HAVING COUNT(j) > 1")
    List<Object[]> findDuplicateActivePostings();

    /** Every distinct voucherId with an active posted journal for a given voucherType — used to check every POSTED source transaction has one. */
    @Query("SELECT DISTINCT j.voucherId FROM JournalHeader j WHERE j.voucherType = :voucherType " +
            "AND j.status = com.storehub.entity.JournalStatus.POSTED AND j.reversalOfJournal IS NULL")
    List<Long> findActivePostedVoucherIds(@Param("voucherType") VoucherType voucherType);

    /** Every distinct (voucherType, voucherId) a journal exists for, for a given source voucherType — used by the orphan-journal Health Check. */
    @Query("SELECT DISTINCT j.voucherId FROM JournalHeader j WHERE j.voucherType = :voucherType AND j.voucherId IS NOT NULL")
    List<Long> findDistinctVoucherIds(@Param("voucherType") VoucherType voucherType);

    /**
     * Every distinct date a POSTED journal exists on that no defined FinancialYear covers.
     * Should be empty going forward since AccountingService.buildAndSaveJournal/reverseJournal
     * resolve a FinancialYear for every posting date before it can be saved; a non-empty result
     * means historical data predates the Financial Year feature, or a FY was deleted after posting.
     */
    @Query("SELECT DISTINCT j.journalDate FROM JournalHeader j WHERE j.status = com.storehub.entity.JournalStatus.POSTED " +
            "AND NOT EXISTS (SELECT 1 FROM FinancialYear f WHERE j.journalDate BETWEEN f.startDate AND f.endDate)")
    List<LocalDate> findPostedDatesWithoutFinancialYear();
}
