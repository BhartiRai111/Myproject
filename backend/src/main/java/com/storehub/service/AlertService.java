package com.storehub.service;

import com.storehub.dto.AlertItem;
import com.storehub.dto.FinancialYearResponse;
import com.storehub.dto.HealthCheckFinding;
import com.storehub.dto.OutstandingBillReportResponse;
import com.storehub.entity.HealthCheckStatus;
import com.storehub.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Notification / Alert Center (Phase 6 spec section 24) — a read-only view
 * that aggregates signals already computed by existing services
 * (InventoryRepository's stock counts, {@link OutstandingBillService}'s
 * ageing, {@link FinancialYearService}, {@link AccountingHealthCheckService}).
 * Deliberately no new business logic and no stored "notification" rows:
 * everything here is derived fresh from the same source of truth its own
 * report/page already uses, so an alert can never drift from what the
 * underlying page shows. Kept to one alert per condition (not one per row)
 * so the list stays meaningful rather than noisy.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final long FY_NEARING_CLOSE_DAYS = 30;

    private final InventoryRepository inventoryRepository;
    private final OutstandingBillService outstandingBillService;
    private final FinancialYearService financialYearService;
    private final AccountingHealthCheckService accountingHealthCheckService;

    @Transactional(readOnly = true)
    public List<AlertItem> getAlerts() {
        List<AlertItem> alerts = new ArrayList<>();

        long outOfStock = inventoryRepository.countOutOfStock();
        if (outOfStock > 0) {
            alerts.add(AlertItem.builder().severity("CRITICAL").category("Inventory")
                    .message(outOfStock + " product(s) are OUT OF STOCK")
                    .path("/inventory?stockStatus=OUT_OF_STOCK").build());
        }

        long lowStock = inventoryRepository.countLowStock();
        if (lowStock > 0) {
            alerts.add(AlertItem.builder().severity("WARNING").category("Inventory")
                    .message(lowStock + " product(s) are LOW STOCK")
                    .path("/inventory?stockStatus=LOW_STOCK").build());
        }

        int reorderCandidates = inventoryRepository.findReorderCandidates().size();
        if (reorderCandidates > 0) {
            alerts.add(AlertItem.builder().severity("WARNING").category("Inventory")
                    .message(reorderCandidates + " product(s) have reached their reorder point")
                    .path("/inventory").build());
        }

        addOutstandingAlert(alerts, outstandingBillService.customerOutstanding(LocalDate.now()),
                "Receivable", "/accounting/reports/receivable");
        addOutstandingAlert(alerts, outstandingBillService.supplierOutstanding(LocalDate.now()),
                "Payable", "/accounting/reports/payable");

        FinancialYearResponse currentFy = financialYearService.getCurrent();
        if (currentFy != null && currentFy.getEndDate() != null) {
            long daysToClose = ChronoUnit.DAYS.between(LocalDate.now(), currentFy.getEndDate());
            if (daysToClose >= 0 && daysToClose <= FY_NEARING_CLOSE_DAYS) {
                alerts.add(AlertItem.builder().severity("WARNING").category("Financial Year")
                        .message("Financial year " + currentFy.getName() + " closes in " + daysToClose + " day(s)")
                        .path("/admin/financial-years").build());
            }
        }

        for (HealthCheckFinding finding : accountingHealthCheckService.runHealthCheck().getFindings()) {
            if (finding.getStatus() == HealthCheckStatus.WARNING || finding.getStatus() == HealthCheckStatus.ERROR) {
                alerts.add(AlertItem.builder()
                        .severity(finding.getStatus() == HealthCheckStatus.ERROR ? "CRITICAL" : "WARNING")
                        .category("Accounting Health Check")
                        .message(finding.getCheckName() + ": " + finding.getMessage())
                        .path("/accounting/reports/health-check").build());
            }
        }

        return alerts;
    }

    private void addOutstandingAlert(List<AlertItem> alerts, OutstandingBillReportResponse report, String category, String path) {
        long overdueCount = 0;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        for (var bucket : report.getAgeingSummary()) {
            if (!"0-30 Days".equals(bucket.getBucket())) {
                overdueCount += bucket.getCount();
                overdueAmount = overdueAmount.add(bucket.getAmount());
            }
        }
        if (overdueCount > 0) {
            alerts.add(AlertItem.builder().severity("WARNING").category(category)
                    .message(overdueCount + " " + category.toLowerCase() + " bill(s) overdue (30+ days), totalling " + overdueAmount)
                    .path(path).build());
        }
    }
}
