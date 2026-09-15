package com.storehub.repository;

import com.storehub.entity.CustomerLedgerEntry;
import com.storehub.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    List<CustomerLedgerEntry> findByCustomerIdOrderByEntryDateAscCreatedAtAsc(Long customerId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.storehub.entity.LedgerEntryType.DEBIT THEN e.amount ELSE -e.amount END), 0) " +
            "FROM CustomerLedgerEntry e WHERE e.customer.id = :customerId")
    BigDecimal getOutstandingForCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.storehub.entity.LedgerEntryType.DEBIT THEN e.amount ELSE -e.amount END), 0) " +
            "FROM CustomerLedgerEntry e")
    BigDecimal getTotalOutstanding();

    List<CustomerLedgerEntry> findByReferenceTypeAndReferenceId(
            com.storehub.entity.LedgerReferenceType referenceType, Long referenceId);

    /** Per-customer outstanding, grouped — used by the Accounting Health Check to compare against the journal control account. */
    @Query("SELECT e.customer.id, COALESCE(SUM(CASE WHEN e.entryType = com.storehub.entity.LedgerEntryType.DEBIT THEN e.amount ELSE -e.amount END), 0) " +
            "FROM CustomerLedgerEntry e GROUP BY e.customer.id")
    List<Object[]> sumByCustomer();
}
