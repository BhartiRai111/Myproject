package com.storehub.service;

import com.storehub.dto.PartyLedgerResponse;
import com.storehub.dto.PartyLedgerRow;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.Customer;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.Supplier;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.CustomerRepository;
import com.storehub.repository.JournalDetailRepository;
import com.storehub.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified Party Ledger (spec section 11): a running-balance statement for one
 * customer or supplier, built from the same accounting journal as the Account
 * Ledger — filtered to the {@code CUSTOMER_RECEIVABLE}/{@code SUPPLIER_PAYABLE}
 * control-account lines tagged with this party (JournalDetail.partyType/partyId).
 * This is a second VIEW over the existing journal, not a second ledger: the
 * operational CustomerLedgerEntry/SupplierLedgerEntry tables (Phase 1's own
 * parallel sub-ledger) remain untouched and keep reconciling against this.
 */
@Service
@RequiredArgsConstructor
public class PartyLedgerService {

    private final JournalDetailRepository journalDetailRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public PartyLedgerResponse partyLedger(AccountingPartyType partyType, Long partyId, LocalDate fromDate, LocalDate toDate) {
        String partyName = resolvePartyName(partyType, partyId);

        BigDecimal openingSigned = BigDecimal.ZERO;
        if (fromDate != null) {
            List<Object[]> before = journalDetailRepository.sumByPartyBefore(partyType, fromDate);
            for (Object[] r : before) {
                if (partyId.equals(r[0])) {
                    openingSigned = ((BigDecimal) r[1]).subtract((BigDecimal) r[2]);
                    break;
                }
            }
        }

        List<JournalDetail> lines = journalDetailRepository.findLedgerLinesByParty(partyType, partyId, fromDate, toDate);

        BigDecimal running = openingSigned;
        List<PartyLedgerRow> rows = new ArrayList<>();
        for (JournalDetail line : lines) {
            JournalHeader journal = line.getJournal();
            running = running.add(line.getDebitAmount()).subtract(line.getCreditAmount());
            rows.add(PartyLedgerRow.builder()
                    .voucherDate(journal.getJournalDate())
                    .voucherType(journal.getVoucherType())
                    .voucherNumber(journal.getVoucherNumber())
                    .particulars(line.getNarration() != null ? line.getNarration() : journal.getNarration())
                    .debit(line.getDebitAmount())
                    .credit(line.getCreditAmount())
                    .balance(running.abs())
                    .balanceType(running.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                    .build());
        }

        return PartyLedgerResponse.builder()
                .partyType(partyType)
                .partyId(partyId)
                .partyName(partyName)
                .fromDate(fromDate)
                .toDate(toDate)
                .openingBalance(openingSigned.abs())
                .openingBalanceType(openingSigned.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                .rows(rows)
                .closingBalance(running.abs())
                .closingBalanceType(running.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                .build();
    }

    private String resolvePartyName(AccountingPartyType partyType, Long partyId) {
        if (partyType == AccountingPartyType.CUSTOMER) {
            Customer customer = customerRepository.findById(partyId)
                    .orElseThrow(() -> new BadRequestException("Customer not found: " + partyId));
            String lastName = customer.getLastName() != null ? customer.getLastName() : "";
            return (customer.getFirstName() + " " + lastName).trim();
        }
        if (partyType == AccountingPartyType.SUPPLIER) {
            Supplier supplier = supplierRepository.findById(partyId)
                    .orElseThrow(() -> new BadRequestException("Supplier not found: " + partyId));
            return supplier.getName();
        }
        throw new BadRequestException("Unsupported party type: " + partyType);
    }
}
