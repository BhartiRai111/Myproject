package com.storehub.repository;

import com.storehub.entity.PurchaseGstEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseGstEntryRepository extends JpaRepository<PurchaseGstEntry, Long> {
    List<PurchaseGstEntry> findByPurchaseId(Long purchaseId);
}
