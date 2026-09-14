package com.storehub.repository;

import com.storehub.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Line-item level queries for GST reporting (HSN summary, tax-rate summary).
 * Always scoped to POSTED, GST-reportable purchases — a Kacchi Purchase's
 * items are excluded by the same {@code gstReportingApplicable} flag used
 * everywhere else, never by re-deriving eligibility from the tax amount.
 */
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    @Query("SELECT COALESCE(h.hsnCode, 'N/A'), COALESCE(h.description, ''), COALESCE(p.unit, ''), " +
            "SUM(i.quantity), SUM(i.taxableAmount), SUM(i.cgstAmount), SUM(i.sgstAmount), SUM(i.igstAmount), SUM(i.subtotal) " +
            "FROM PurchaseItem i JOIN i.purchase pu JOIN i.product p LEFT JOIN p.hsn h " +
            "WHERE pu.status = com.storehub.entity.PurchaseStatus.COMPLETED " +
            "AND pu.gstReportingApplicable = true " +
            "AND (:fromDate IS NULL OR pu.purchaseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR pu.purchaseDate <= :toDate) " +
            "GROUP BY h.hsnCode, h.description, p.unit " +
            "ORDER BY h.hsnCode ASC")
    List<Object[]> hsnSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT i.gstPercent, SUM(i.taxableAmount), SUM(i.cgstAmount), SUM(i.sgstAmount), SUM(i.igstAmount), SUM(i.subtotal) " +
            "FROM PurchaseItem i JOIN i.purchase pu " +
            "WHERE pu.status = com.storehub.entity.PurchaseStatus.COMPLETED " +
            "AND pu.gstReportingApplicable = true " +
            "AND (:fromDate IS NULL OR pu.purchaseDate >= :fromDate) " +
            "AND (:toDate IS NULL OR pu.purchaseDate <= :toDate) " +
            "GROUP BY i.gstPercent " +
            "ORDER BY i.gstPercent ASC")
    List<Object[]> taxRateSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
