package com.storehub.service;

import com.storehub.dto.AgeingBucketSummary;
import com.storehub.dto.OutstandingBillDetailRow;
import com.storehub.dto.OutstandingBillReportResponse;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.Purchase;
import com.storehub.entity.Sale;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill-wise Outstanding + Ageing (spec sections 14-15), built directly from
 * Sale.dueAmount/Purchase.payableAmount — both fields ReceiptService/PaymentService
 * already keep in exact sync with ReceiptAllocation/PaymentAllocation (see
 * their applyAllocation/reverseAndRemove methods), so this report reuses that
 * allocation math rather than re-deriving it.
 */
@Service
@RequiredArgsConstructor
public class OutstandingBillService {

    private static final String AGEING_BASIS = "INVOICE_DATE";
    private static final String[] BUCKET_LABELS = {"0-30 Days", "31-60 Days", "61-90 Days", "91-180 Days", "180+ Days"};

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public OutstandingBillReportResponse customerOutstanding(LocalDate asOfDate) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        List<Sale> outstanding = saleRepository.findAllOutstanding();

        List<OutstandingBillDetailRow> rows = new ArrayList<>();
        for (Sale sale : outstanding) {
            long days = ChronoUnit.DAYS.between(sale.getSaleDate(), effectiveAsOf);
            String customerName = sale.getCustomer() != null
                    ? (sale.getCustomer().getFirstName() + " " + nullToEmpty(sale.getCustomer().getLastName())).trim()
                    : "Walk-in Customer";
            rows.add(OutstandingBillDetailRow.builder()
                    .partyId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                    .partyName(customerName)
                    .billId(sale.getId())
                    .invoiceNumber(sale.getInvoiceNumber())
                    .invoiceDate(sale.getSaleDate())
                    .invoiceAmount(sale.getTotalAmount())
                    .receivedOrPaidAmount(sale.getPaidAmount())
                    .outstanding(sale.getDueAmount())
                    .daysOutstanding(days)
                    .ageingBucket(bucketFor(days))
                    .build());
        }

        return build(AccountingPartyType.CUSTOMER, effectiveAsOf, rows);
    }

    @Transactional(readOnly = true)
    public OutstandingBillReportResponse supplierOutstanding(LocalDate asOfDate) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        List<Purchase> outstanding = purchaseRepository.findAllOutstanding();

        List<OutstandingBillDetailRow> rows = new ArrayList<>();
        for (Purchase purchase : outstanding) {
            long days = ChronoUnit.DAYS.between(purchase.getPurchaseDate(), effectiveAsOf);
            rows.add(OutstandingBillDetailRow.builder()
                    .partyId(purchase.getSupplier().getId())
                    .partyName(purchase.getSupplier().getName())
                    .billId(purchase.getId())
                    .invoiceNumber(purchase.getPurchaseNumber())
                    .invoiceDate(purchase.getPurchaseDate())
                    .invoiceAmount(purchase.getTotalAmount())
                    .receivedOrPaidAmount(purchase.getPaidAmount())
                    .outstanding(purchase.getPayableAmount())
                    .daysOutstanding(days)
                    .ageingBucket(bucketFor(days))
                    .build());
        }

        return build(AccountingPartyType.SUPPLIER, effectiveAsOf, rows);
    }

    private OutstandingBillReportResponse build(AccountingPartyType partyType, LocalDate asOfDate, List<OutstandingBillDetailRow> rows) {
        Map<String, AgeingBucketSummary> bucketTotals = new LinkedHashMap<>();
        for (String label : BUCKET_LABELS) {
            bucketTotals.put(label, AgeingBucketSummary.builder().bucket(label).count(0).amount(BigDecimal.ZERO).build());
        }

        BigDecimal totalInvoice = BigDecimal.ZERO;
        BigDecimal totalReceivedOrPaid = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        for (OutstandingBillDetailRow row : rows) {
            totalInvoice = totalInvoice.add(row.getInvoiceAmount());
            totalReceivedOrPaid = totalReceivedOrPaid.add(row.getReceivedOrPaidAmount());
            totalOutstanding = totalOutstanding.add(row.getOutstanding());

            AgeingBucketSummary existing = bucketTotals.get(row.getAgeingBucket());
            bucketTotals.put(row.getAgeingBucket(), AgeingBucketSummary.builder()
                    .bucket(existing.getBucket())
                    .count(existing.getCount() + 1)
                    .amount(existing.getAmount().add(row.getOutstanding()))
                    .build());
        }

        return OutstandingBillReportResponse.builder()
                .partyType(partyType)
                .asOfDate(asOfDate)
                .ageingBasis(AGEING_BASIS)
                .rows(rows)
                .ageingSummary(new ArrayList<>(bucketTotals.values()))
                .totalInvoiceAmount(totalInvoice)
                .totalReceivedOrPaid(totalReceivedOrPaid)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    private String bucketFor(long days) {
        if (days <= 30) return BUCKET_LABELS[0];
        if (days <= 60) return BUCKET_LABELS[1];
        if (days <= 90) return BUCKET_LABELS[2];
        if (days <= 180) return BUCKET_LABELS[3];
        return BUCKET_LABELS[4];
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
