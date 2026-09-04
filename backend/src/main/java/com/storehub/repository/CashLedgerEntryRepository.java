package com.storehub.repository;

import com.storehub.entity.CashLedgerEntry;
import com.storehub.entity.LedgerReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CashLedgerEntryRepository extends JpaRepository<CashLedgerEntry, Long> {

    List<CashLedgerEntry> findByReferenceTypeAndReferenceId(LedgerReferenceType referenceType, Long referenceId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM CashLedgerEntry e WHERE e.paymentMode = :paymentMode")
    BigDecimal getBalanceForMode(@Param("paymentMode") com.storehub.entity.PaymentMode paymentMode);
}
