package com.storehub.service;

import com.storehub.dto.CashTransactionCreateRequest;
import com.storehub.dto.CashTransactionResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.CashTransaction;
import com.storehub.entity.CashTransactionStatus;
import com.storehub.entity.CashTransactionType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.CashTransactionNotFoundException;
import com.storehub.repository.CashTransactionRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ad-hoc Cash In / Cash Out (Phase 6 spec section 12) — posts through the
 * same centralized {@link AccountingService#postJournal} every other
 * voucher uses; the existing Cash Book/Bank Book (Phase 4,
 * {@link CashBankBookService}) reads these straight off the journal, so no
 * separate cash balance is maintained here.
 */
@Service
@RequiredArgsConstructor
public class CashTransactionService {

    private final CashTransactionRepository cashTransactionRepository;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AccountingService accountingService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PagedResponse<CashTransactionResponse> search(String search, CashTransactionType type, CashTransactionStatus status,
                                                           LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CashTransactionResponse> result = cashTransactionRepository.search(search, type, status, fromDate, toDate, pageable)
                .map(CashTransactionResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public CashTransactionResponse getById(Long id) {
        return CashTransactionResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public CashTransactionResponse create(CashTransactionCreateRequest request) {
        CashTransaction txn = CashTransaction.builder()
                .transactionDate(request.getTransactionDate())
                .transactionType(request.getTransactionType())
                .paymentMode(request.getPaymentMode())
                .amount(request.getAmount())
                .reason(request.getReason())
                .financialYearId(financialYearService.resolveForDate(request.getTransactionDate()).getId())
                .createdBy(SecurityUtil.currentUsername())
                .status(CashTransactionStatus.DRAFT)
                .build();

        CashTransaction saved = cashTransactionRepository.save(txn);
        saved.setTransactionNumber(voucherNumberService.next(VoucherDocType.CASH_TRANSACTION, request.getTransactionDate()));
        saved = cashTransactionRepository.save(saved);

        auditService.log(AuditAction.CREATE, "CASH", "CashTransaction", saved.getId(), saved.getTransactionNumber(),
                null, null, "Cash transaction " + saved.getTransactionNumber() + " (" + saved.getTransactionType() + ") created");

        if (request.isPost()) {
            return post(saved.getId());
        }
        return CashTransactionResponse.fromEntity(saved);
    }

    @Transactional
    public CashTransactionResponse post(Long id) {
        CashTransaction txn = findOrThrow(id);
        if (txn.getStatus() != CashTransactionStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT cash transaction can be posted (current status: " + txn.getStatus() + ")");
        }

        SystemAccountCode cashOrBank = accountingService.resolveCashOrBank(txn.getPaymentMode());
        List<JournalLine> lines;
        if (txn.getTransactionType() == CashTransactionType.CASH_IN) {
            lines = List.of(
                    JournalLine.debit(cashOrBank, txn.getAmount()),
                    JournalLine.credit(SystemAccountCode.OTHER_INCOME, txn.getAmount()));
        } else {
            lines = List.of(
                    JournalLine.debit(SystemAccountCode.EXPENSES, txn.getAmount()),
                    JournalLine.credit(cashOrBank, txn.getAmount()));
        }

        accountingService.postJournal(VoucherType.CASH_TRANSACTION, txn.getId(), txn.getTransactionNumber(),
                txn.getTransactionDate(), "Cash " + (txn.getTransactionType() == CashTransactionType.CASH_IN ? "In" : "Out")
                        + ": " + txn.getReason(), lines);

        txn.setStatus(CashTransactionStatus.POSTED);
        txn.setPostedBy(SecurityUtil.currentUsername());
        txn.setPostedAt(LocalDateTime.now());
        CashTransaction posted = cashTransactionRepository.save(txn);

        auditService.log(AuditAction.POST, "CASH", "CashTransaction", posted.getId(), posted.getTransactionNumber(),
                null, null, "Cash transaction " + posted.getTransactionNumber() + " posted");
        return CashTransactionResponse.fromEntity(posted);
    }

    /** Idempotent: cancelling an already-CANCELLED transaction is a no-op that returns its current state. */
    @Transactional
    public CashTransactionResponse cancel(Long id) {
        CashTransaction txn = findOrThrow(id);
        if (txn.getStatus() == CashTransactionStatus.CANCELLED) {
            return CashTransactionResponse.fromEntity(txn);
        }

        String reason = "Cash transaction cancelled: " + txn.getTransactionNumber();
        if (txn.getStatus() == CashTransactionStatus.POSTED) {
            accountingService.reverseJournal(VoucherType.CASH_TRANSACTION, txn.getId(), reason);
        }

        txn.setStatus(CashTransactionStatus.CANCELLED);
        txn.setCancelledBy(SecurityUtil.currentUsername());
        txn.setCancelledAt(LocalDateTime.now());
        CashTransaction cancelled = cashTransactionRepository.save(txn);

        auditService.log(AuditAction.CANCEL, "CASH", "CashTransaction", cancelled.getId(), cancelled.getTransactionNumber(),
                null, null, reason);
        return CashTransactionResponse.fromEntity(cancelled);
    }

    private CashTransaction findOrThrow(Long id) {
        return cashTransactionRepository.findById(id).orElseThrow(() -> new CashTransactionNotFoundException(id));
    }
}
