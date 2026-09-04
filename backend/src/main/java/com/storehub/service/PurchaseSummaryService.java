package com.storehub.service;

import com.storehub.dto.PurchaseSummaryResponse;
import com.storehub.repository.PurchaseOrderRepository;
import com.storehub.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PurchaseSummaryService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseRepository purchaseRepository;
    private final LedgerService ledgerService;
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseSummaryResponse getSummary() {
        return PurchaseSummaryResponse.builder()
                .totalPurchaseOrders(purchaseOrderRepository.count())
                .pendingOrders(purchaseOrderService.countPendingOrders())
                .totalPurchaseBills(purchaseRepository.count())
                .totalPayables(ledgerService.getTotalPayables())
                .todaysPurchases(purchaseRepository.getTotalPurchasesForDate(LocalDate.now()))
                .build();
    }
}
