package com.storehub.service;

import com.storehub.dto.ReceivablePayableResponse;
import com.storehub.dto.ReceivablePayableRow;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.Customer;
import com.storehub.entity.Supplier;
import com.storehub.entity.VoucherType;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.JournalDetailRepository;
import com.storehub.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Receivable (spec section 12) and Payable (section 13) reports, both built
 * from the SAME accounting-journal control-account movements the Party Ledger
 * reads — never a bare "Sales minus Receipts" sum. Opening balance is every
 * posted/reversed control-account line strictly before {@code fromDate};
 * the transaction/payment columns are the movement within [fromDate, toDate].
 * A CUSTOMER_RECEIVABLE line's debit side is the "increase" (a sale); a
 * SUPPLIER_PAYABLE line's credit side is the "increase" (a purchase) — the
 * two reports share this method with that polarity flipped.
 */
@Service
@RequiredArgsConstructor
public class ReceivablePayableService {

    private final JournalDetailRepository journalDetailRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public ReceivablePayableResponse receivable(LocalDate fromDate, LocalDate toDate) {
        return build(AccountingPartyType.CUSTOMER, VoucherType.SALE, VoucherType.RECEIPT, VoucherType.CREDIT_NOTE, false, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public ReceivablePayableResponse payable(LocalDate fromDate, LocalDate toDate) {
        return build(AccountingPartyType.SUPPLIER, VoucherType.PURCHASE, VoucherType.PAYMENT, VoucherType.DEBIT_NOTE, true, fromDate, toDate);
    }

    private ReceivablePayableResponse build(AccountingPartyType partyType, VoucherType transactionRef, VoucherType paymentRef,
                                             VoucherType noteRef, boolean creditIncreases, LocalDate fromDate, LocalDate toDate) {
        Map<Long, BigDecimal[]> openingByParty = new HashMap<>();
        if (fromDate != null) {
            for (Object[] r : journalDetailRepository.sumByPartyBefore(partyType, fromDate)) {
                openingByParty.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
            }
        }

        Map<Long, Map<VoucherType, BigDecimal[]>> activityByParty = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumByPartyAndReferenceTypeRange(partyType, fromDate, toDate)) {
            Long partyId = (Long) r[0];
            VoucherType refType = (VoucherType) r[1];
            activityByParty.computeIfAbsent(partyId, k -> new HashMap<>())
                    .put(refType, new BigDecimal[]{(BigDecimal) r[2], (BigDecimal) r[3]});
        }

        Set<Long> partyIds = new HashSet<>();
        partyIds.addAll(openingByParty.keySet());
        partyIds.addAll(activityByParty.keySet());

        Map<Long, String> names = resolveNames(partyType, partyIds);

        BigDecimal totalOpening = BigDecimal.ZERO;
        BigDecimal totalTransactions = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        List<ReceivablePayableRow> rows = new ArrayList<>();

        for (Long partyId : partyIds) {
            BigDecimal[] opening = openingByParty.getOrDefault(partyId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal openingSigned = creditIncreases ? opening[1].subtract(opening[0]) : opening[0].subtract(opening[1]);

            Map<VoucherType, BigDecimal[]> activity = activityByParty.getOrDefault(partyId, Map.of());
            BigDecimal[] txn = activity.getOrDefault(transactionRef, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] pay = activity.getOrDefault(paymentRef, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] note = activity.getOrDefault(noteRef, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            // Net both sides of each bucket, not just the "increasing" side: a reversed Sale/Purchase
            // posts its offsetting entry as the opposite side of the SAME referenceType bucket (see
            // AccountingService.reverseJournal), so reading only one side would keep counting a
            // cancelled voucher's original amount forever.
            BigDecimal transactionAmount = creditIncreases ? txn[1].subtract(txn[0]) : txn[0].subtract(txn[1]);
            // A Credit/Debit Note always REDUCES the outstanding balance — the same polarity as a
            // Receipt/Payment (see CreditNoteService/DebitNoteService's journal lines), so it nets
            // the same way paymentAmount does.
            BigDecimal paymentAmount = creditIncreases ? pay[0].subtract(pay[1]) : pay[1].subtract(pay[0]);
            BigDecimal noteAmount = creditIncreases ? note[0].subtract(note[1]) : note[1].subtract(note[0]);
            BigDecimal closing = openingSigned.add(transactionAmount).subtract(paymentAmount).subtract(noteAmount);

            rows.add(ReceivablePayableRow.builder()
                    .partyId(partyId)
                    .partyName(names.getOrDefault(partyId, "Unknown"))
                    .openingBalance(openingSigned)
                    .transactionAmount(transactionAmount)
                    .paymentAmount(paymentAmount)
                    .creditNoteAmount(creditIncreases ? BigDecimal.ZERO : noteAmount)
                    .debitNoteAmount(creditIncreases ? noteAmount : BigDecimal.ZERO)
                    .closingOutstanding(closing)
                    .build());

            totalOpening = totalOpening.add(openingSigned);
            totalTransactions = totalTransactions.add(transactionAmount);
            totalPayments = totalPayments.add(paymentAmount);
            totalOutstanding = totalOutstanding.add(closing);
        }

        rows.sort(Comparator.comparing(ReceivablePayableRow::getPartyName, String.CASE_INSENSITIVE_ORDER));

        return ReceivablePayableResponse.builder()
                .partyType(partyType)
                .fromDate(fromDate)
                .toDate(toDate)
                .rows(rows)
                .totalOpening(totalOpening)
                .totalTransactions(totalTransactions)
                .totalPayments(totalPayments)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    private Map<Long, String> resolveNames(AccountingPartyType partyType, Set<Long> partyIds) {
        Map<Long, String> names = new HashMap<>();
        if (partyIds.isEmpty()) {
            return names;
        }
        if (partyType == AccountingPartyType.CUSTOMER) {
            for (Customer c : customerRepository.findAllById(partyIds)) {
                String lastName = c.getLastName() != null ? c.getLastName() : "";
                names.put(c.getId(), (c.getFirstName() + " " + lastName).trim());
            }
        } else {
            for (Supplier s : supplierRepository.findAllById(partyIds)) {
                names.put(s.getId(), s.getName());
            }
        }
        return names;
    }
}
