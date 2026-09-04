package com.storehub.repository;

import com.storehub.entity.GstEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GstEntryRepository extends JpaRepository<GstEntry, Long> {
    List<GstEntry> findBySaleId(Long saleId);
}
