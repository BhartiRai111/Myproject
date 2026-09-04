package com.storehub.service;

import com.storehub.dto.SalesSummaryResponse;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SalesSummaryService {

    private final SalesOrderRepository salesOrderRepository;
    private final SaleRepository saleRepository;
    private final LedgerService ledgerService;
    private final SalesOrderService salesOrderService;

    public SalesSummaryResponse getSummary() {
        return SalesSummaryResponse.builder()
                .totalSalesOrders(salesOrderRepository.count())
                .pendingOrders(salesOrderService.countPendingOrders())
                .totalSalesBills(saleRepository.count())
                .totalReceivables(ledgerService.getTotalOutstanding())
                .todaysSales(saleRepository.getTotalSalesForDate(LocalDate.now()))
                .build();
    }
}
