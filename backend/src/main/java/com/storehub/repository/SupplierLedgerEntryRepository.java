package com.storehub.repository;

import com.storehub.entity.SupplierLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierLedgerEntryRepository extends JpaRepository<SupplierLedgerEntry, Long> {

    List<SupplierLedgerEntry> findBySupplierIdOrderByEntryDateAscCreatedAtAsc(Long supplierId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.storehub.entity.LedgerEntryType.CREDIT THEN e.amount ELSE -e.amount END), 0) " +
            "FROM SupplierLedgerEntry e WHERE e.supplier.id = :supplierId")
    BigDecimal getOutstandingForSupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.storehub.entity.LedgerEntryType.CREDIT THEN e.amount ELSE -e.amount END), 0) " +
            "FROM SupplierLedgerEntry e")
    BigDecimal getTotalOutstanding();

    List<SupplierLedgerEntry> findByReferenceTypeAndReferenceId(
            com.storehub.entity.LedgerReferenceType referenceType, Long referenceId);
}
