package com.storehub.repository;

import com.storehub.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Line-item level queries for GST reporting (HSN summary, tax-rate summary).
 * Always scoped to POSTED, GST-reportable sales — a Kacchi Sale's items are
 * excluded by the same {@code gstReportingApplicable} flag used everywhere
 * else, never by re-deriving eligibility from the tax amount.
 */
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("SELECT COALESCE(h.hsnCode, 'N/A'), COALESCE(h.description, ''), COALESCE(p.unit, ''), " +
            "SUM(i.quantity), SUM(i.taxableAmount), SUM(i.cgstAmount), SUM(i.sgstAmount), SUM(i.igstAmount), SUM(i.subtotal) " +
            "FROM SaleItem i JOIN i.sale s JOIN i.product p LEFT JOIN p.hsn h " +
            "WHERE s.status = com.storehub.entity.SaleStatus.COMPLETED " +
            "AND s.gstReportingApplicable = true " +
            "AND (:fromDate IS NULL OR s.saleDate >= :fromDate) " +
            "AND (:toDate IS NULL OR s.saleDate <= :toDate) " +
            "AND (:storeId IS NULL OR s.store.id = :storeId) " +
            "GROUP BY h.hsnCode, h.description, p.unit " +
            "ORDER BY h.hsnCode ASC")
    List<Object[]> hsnSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("storeId") Long storeId);

    @Query("SELECT i.gstPercent, SUM(i.taxableAmount), SUM(i.cgstAmount), SUM(i.sgstAmount), SUM(i.igstAmount), SUM(i.subtotal) " +
            "FROM SaleItem i JOIN i.sale s " +
            "WHERE s.status = com.storehub.entity.SaleStatus.COMPLETED " +
            "AND s.gstReportingApplicable = true " +
            "AND (:fromDate IS NULL OR s.saleDate >= :fromDate) " +
            "AND (:toDate IS NULL OR s.saleDate <= :toDate) " +
            "AND (:storeId IS NULL OR s.store.id = :storeId) " +
            "GROUP BY i.gstPercent " +
            "ORDER BY i.gstPercent ASC")
    List<Object[]> taxRateSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("storeId") Long storeId);
}
