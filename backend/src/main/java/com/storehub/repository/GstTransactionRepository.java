package com.storehub.repository;

import com.storehub.entity.GstTransaction;
import com.storehub.entity.GstTransactionStatus;
import com.storehub.entity.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GstTransactionRepository extends JpaRepository<GstTransaction, Long> {

    Optional<GstTransaction> findBySourceTransactionTypeAndSourceTransactionId(VoucherType sourceTransactionType, Long sourceTransactionId);

    List<GstTransaction> findAllBySourceTransactionTypeAndSourceTransactionId(VoucherType sourceTransactionType, Long sourceTransactionId);

    @Query("SELECT g FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType = :type " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:b2b IS NULL OR g.b2b = :b2b) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId) " +
            "ORDER BY g.voucherDate ASC, g.id ASC")
    Page<GstTransaction> search(@Param("type") VoucherType type,
                                 @Param("returnPeriod") String returnPeriod,
                                 @Param("fromDate") LocalDate fromDate,
                                 @Param("toDate") LocalDate toDate,
                                 @Param("b2b") Boolean b2b,
                                 @Param("storeId") Long storeId,
                                 Pageable pageable);

    @Query("SELECT g FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType = :type " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId) " +
            "ORDER BY g.voucherDate ASC, g.id ASC")
    List<GstTransaction> findActiveForSummary(@Param("type") VoucherType type,
                                               @Param("returnPeriod") String returnPeriod,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               @Param("storeId") Long storeId);

    @Query("SELECT COALESCE(SUM(g.taxableAmount),0), COALESCE(SUM(g.cgstAmount),0), COALESCE(SUM(g.sgstAmount),0), " +
            "COALESCE(SUM(g.igstAmount),0), COALESCE(SUM(g.totalTax),0), COALESCE(SUM(g.totalValue),0), COUNT(g) " +
            "FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType = :type " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId)")
    List<Object[]> aggregateTotals(@Param("type") VoucherType type,
                                    @Param("returnPeriod") String returnPeriod,
                                    @Param("fromDate") LocalDate fromDate,
                                    @Param("toDate") LocalDate toDate,
                                    @Param("storeId") Long storeId);

    /** Same shape as {@link #aggregateTotals}, restricted to B2B (valid-GSTIN) rows — used for ITC-eligible totals. */
    @Query("SELECT COALESCE(SUM(g.taxableAmount),0), COALESCE(SUM(g.cgstAmount),0), COALESCE(SUM(g.sgstAmount),0), " +
            "COALESCE(SUM(g.igstAmount),0), COALESCE(SUM(g.totalTax),0), COALESCE(SUM(g.totalValue),0), COUNT(g) " +
            "FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType = :type AND g.b2b = true " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId)")
    List<Object[]> aggregateB2bTotals(@Param("type") VoucherType type,
                                       @Param("returnPeriod") String returnPeriod,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate,
                                       @Param("storeId") Long storeId);

    /**
     * Same as {@link #search}/{@link #aggregateTotals}, but scoped to a SET of voucher types via IN — used only by
     * the ITC-side reports (Input GST report, GSTR-3B ITC, GST Liability input) to fold Expense rows in alongside
     * Purchase without touching any single-type Sale/Purchase-only report or its existing behaviour.
     */
    @Query("SELECT g FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType IN :types " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId) " +
            "ORDER BY g.voucherDate ASC, g.id ASC")
    Page<GstTransaction> searchByTypes(@Param("types") List<VoucherType> types,
                                        @Param("returnPeriod") String returnPeriod,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        @Param("storeId") Long storeId,
                                        Pageable pageable);

    @Query("SELECT COALESCE(SUM(g.taxableAmount),0), COALESCE(SUM(g.cgstAmount),0), COALESCE(SUM(g.sgstAmount),0), " +
            "COALESCE(SUM(g.igstAmount),0), COALESCE(SUM(g.totalTax),0), COALESCE(SUM(g.totalValue),0), COUNT(g) " +
            "FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType IN :types " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId)")
    List<Object[]> aggregateTotalsByTypes(@Param("types") List<VoucherType> types,
                                           @Param("returnPeriod") String returnPeriod,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate,
                                           @Param("storeId") Long storeId);

    /** Same shape as {@link #aggregateTotalsByTypes}, restricted to b2b=true rows — for Purchase this means valid-GSTIN, for Expense this means itcEligible. */
    @Query("SELECT COALESCE(SUM(g.taxableAmount),0), COALESCE(SUM(g.cgstAmount),0), COALESCE(SUM(g.sgstAmount),0), " +
            "COALESCE(SUM(g.igstAmount),0), COALESCE(SUM(g.totalTax),0), COALESCE(SUM(g.totalValue),0), COUNT(g) " +
            "FROM GstTransaction g WHERE g.status = com.storehub.entity.GstTransactionStatus.ACTIVE " +
            "AND g.sourceTransactionType IN :types AND g.b2b = true " +
            "AND (:returnPeriod IS NULL OR g.returnPeriod = :returnPeriod) " +
            "AND (:fromDate IS NULL OR g.voucherDate >= :fromDate) " +
            "AND (:toDate IS NULL OR g.voucherDate <= :toDate) " +
            "AND (:storeId IS NULL OR g.store.id = :storeId)")
    List<Object[]> aggregateB2bTotalsByTypes(@Param("types") List<VoucherType> types,
                                              @Param("returnPeriod") String returnPeriod,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate,
                                              @Param("storeId") Long storeId);

    /** All source ids currently having an ACTIVE reporting row, for reconciliation / duplicate detection. */
    @Query("SELECT g.sourceTransactionId, COUNT(g) FROM GstTransaction g WHERE g.sourceTransactionType = :type " +
            "AND g.status = com.storehub.entity.GstTransactionStatus.ACTIVE GROUP BY g.sourceTransactionId")
    List<Object[]> countActiveBySource(@Param("type") VoucherType type);

    List<GstTransaction> findBySourceTransactionTypeAndStatus(VoucherType type, GstTransactionStatus status);
}
