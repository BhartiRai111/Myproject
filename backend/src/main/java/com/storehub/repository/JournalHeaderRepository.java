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
}
