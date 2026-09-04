package com.storehub.repository;

import com.storehub.entity.ReceiptAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, Long> {
    List<ReceiptAllocation> findBySaleId(Long saleId);
}
