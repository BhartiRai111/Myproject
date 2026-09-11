package com.storehub.service;

import com.storehub.dto.TrialBalanceResponse;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.JournalHeaderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the accounting-engine test scenarios from the Phase 1 spec that
 * cannot be exercised through the normal Sale/Purchase/Receipt/Payment API
 * flows (those never call the posting engine twice for the same source
 * transaction, and never reverse without a cancel/delete). Every test is
 * @Transactional so it rolls back automatically and leaves no trace in the
 * shared dev database.
 */
@SpringBootTest
@Transactional
class AccountingServiceTest {

    @Autowired
    private AccountingService accountingService;

    @Autowired
    private AccountingReportService accountingReportService;

    @Autowired
    private JournalHeaderRepository journalHeaderRepository;

    /** Scenario 6: posting the same voucher twice must reject the second attempt, leaving exactly one journal. */
    @Test
    void duplicatePostingForSameVoucherIsRejected() {
        Long voucherId = -1001L;
        List<JournalLine> lines = List.of(
                JournalLine.debit(SystemAccountCode.CASH, new BigDecimal("100.00")),
                JournalLine.credit(SystemAccountCode.SALES, new BigDecimal("100.00")));

        accountingService.postJournal(VoucherType.SALE, voucherId, "TEST-DUP-001", LocalDate.now(), "first post", lines);

        assertThatThrownBy(() ->
                accountingService.postJournal(VoucherType.SALE, voucherId, "TEST-DUP-001", LocalDate.now(), "second post", lines))
                .isInstanceOf(BadRequestException.class);

        long postedCount = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType.SALE, voucherId, JournalStatus.POSTED)
                .stream().count();
        assertThat(postedCount).isEqualTo(1);
    }

    /** Scenario 7: cancelling never deletes the original journal; it stays auditable and a reversal is posted alongside it. */
    @Test
    void reversalNeverDeletesTheOriginalJournal() {
        Long voucherId = -1002L;
        List<JournalLine> lines = List.of(
                JournalLine.debit(SystemAccountCode.CUSTOMER_RECEIVABLE, new BigDecimal("11800.00"), AccountingPartyType.CUSTOMER, 1L),
                JournalLine.credit(SystemAccountCode.SALES, new BigDecimal("10000.00")),
                JournalLine.credit(SystemAccountCode.OUTPUT_CGST, new BigDecimal("900.00")),
                JournalLine.credit(SystemAccountCode.OUTPUT_SGST, new BigDecimal("900.00")));

        JournalHeader original = accountingService.postJournal(VoucherType.SALE, voucherId, "TEST-REV-001", LocalDate.now(), "original sale", lines);

        accountingService.reverseJournal(VoucherType.SALE, voucherId, "Sale cancelled: TEST-REV-001");

        JournalHeader reloadedOriginal = journalHeaderRepository.findById(original.getId()).orElseThrow();
        assertThat(reloadedOriginal.getStatus()).isEqualTo(JournalStatus.REVERSED);

        List<JournalHeader> allForVoucher = journalHeaderRepository.findAll().stream()
                .filter(j -> j.getVoucherType() == VoucherType.SALE && voucherId.equals(j.getVoucherId()))
                .toList();
        assertThat(allForVoucher).hasSize(2);

        JournalHeader reversal = allForVoucher.stream().filter(j -> j.getReversalOfJournal() != null).findFirst().orElseThrow();
        assertThat(reversal.getStatus()).isEqualTo(JournalStatus.POSTED);
        BigDecimal reversalDebit = reversal.getLines().stream().map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reversalCredit = reversal.getLines().stream().map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(reversalDebit).isEqualByComparingTo(reversalCredit);
        assertThat(reversalDebit).isEqualByComparingTo("11800.00");

        // Posting again for the same voucher after reversal must succeed (not blocked as a false-positive duplicate).
        JournalHeader repost = accountingService.postJournal(VoucherType.SALE, voucherId, "TEST-REV-001", LocalDate.now(), "reposted sale", lines);
        assertThat(repost.getStatus()).isEqualTo(JournalStatus.POSTED);
    }

    /** Scenario 9: the Trial Balance must always show total debit = total credit after any number of postings. */
    @Test
    void trialBalanceStaysBalancedAfterMultipleTransactions() {
        accountingService.postJournal(VoucherType.SALE, -1003L, "TEST-TB-001", LocalDate.now(), "tb sale",
                List.of(JournalLine.debit(SystemAccountCode.CASH, new BigDecimal("500.00")),
                        JournalLine.credit(SystemAccountCode.SALES, new BigDecimal("500.00"))));
        accountingService.postJournal(VoucherType.PURCHASE, -1004L, "TEST-TB-002", LocalDate.now(), "tb purchase",
                List.of(JournalLine.debit(SystemAccountCode.PURCHASE, new BigDecimal("300.00")),
                        JournalLine.credit(SystemAccountCode.SUPPLIER_PAYABLE, new BigDecimal("300.00"), AccountingPartyType.SUPPLIER, 1L)));

        TrialBalanceResponse trialBalance = accountingReportService.trialBalance(LocalDate.now());

        assertThat(trialBalance.getTotalDebit()).isEqualByComparingTo(trialBalance.getTotalCredit());
        assertThat(trialBalance.isBalanced()).isTrue();
    }
}
