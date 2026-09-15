package com.storehub.service;

import com.storehub.dto.CreditNoteCreateRequest;
import com.storehub.dto.CreditNoteItemRequest;
import com.storehub.dto.CreditNoteResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.CreditNote;
import com.storehub.entity.CreditNoteItem;
import com.storehub.entity.NoteStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleItem;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.StockImpactType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.NoteNotFoundException;
import com.storehub.exception.SaleNotFoundException;
import com.storehub.repository.CreditNoteItemRepository;
import com.storehub.repository.CreditNoteRepository;
import com.storehub.repository.SaleItemRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales Credit Note lifecycle (Phase 5 spec sections 5-10): DRAFT has zero
 * side effects; POSTED reuses the SAME centralized engines every other
 * document uses — {@link AccountingService#postJournal} (which itself
 * enforces the financial-year-open rule and duplicate-posting protection),
 * {@link InventoryService#applyMovement}, {@link LedgerService}, and
 * {@link GstTransactionSyncService} — never a parallel implementation.
 * CANCELLED reverses every effect POSTED applied, and is idempotent.
 */
@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteItemRepository creditNoteItemRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final LedgerService ledgerService;
    private final GstTransactionSyncService gstTransactionSyncService;

    @Transactional(readOnly = true)
    public PagedResponse<CreditNoteResponse> search(String search, NoteStatus status, LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CreditNoteResponse> result = creditNoteRepository.search(search, status, fromDate, toDate, pageable)
                .map(CreditNoteResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse getById(Long id) {
        return CreditNoteResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public CreditNoteResponse create(CreditNoteCreateRequest request) {
        Sale sale = saleRepository.findById(request.getSourceSaleId())
                .orElseThrow(() -> new SaleNotFoundException(request.getSourceSaleId()));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("Cannot create a credit note against a cancelled sale");
        }
        if (sale.getStatus() == SaleStatus.DRAFT) {
            throw new BadRequestException("Cannot create a credit note against a sale that has not been posted");
        }
        if (sale.getCustomer() == null) {
            throw new BadRequestException("Cannot create a credit note against a walk-in sale with no customer on record");
        }

        CreditNote note = CreditNote.builder()
                .noteType(request.getNoteType())
                .sourceSale(sale)
                .customer(sale.getCustomer())
                .noteDate(request.getNoteDate())
                .financialYearId(financialYearService.resolveForDate(request.getNoteDate()).getId())
                .reason(request.getReason())
                .gstType(sale.getGstType())
                .stockImpact(request.getStockImpact())
                .gstReportingApplicable(sale.getGstReportingApplicable())
                .remarks(request.getRemarks())
                .createdBy(SecurityUtil.currentUsername())
                .status(NoteStatus.DRAFT)
                .taxableAmount(BigDecimal.ZERO)
                .cgstAmount(BigDecimal.ZERO)
                .sgstAmount(BigDecimal.ZERO)
                .igstAmount(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalTaxable = BigDecimal.ZERO, totalCgst = BigDecimal.ZERO, totalSgst = BigDecimal.ZERO, totalIgst = BigDecimal.ZERO;
        for (CreditNoteItemRequest itemReq : request.getItems()) {
            SaleItem saleItem = saleItemRepository.findById(itemReq.getSaleItemId())
                    .orElseThrow(() -> new BadRequestException("Sale item not found: " + itemReq.getSaleItemId()));
            if (!saleItem.getSale().getId().equals(sale.getId())) {
                throw new BadRequestException("Sale item " + saleItem.getId() + " does not belong to sale " + sale.getInvoiceNumber());
            }
            if (itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than 0");
            }

            int alreadyReturned = creditNoteItemRepository.sumReturnedQuantity(saleItem.getId());
            int eligible = saleItem.getQuantity() - alreadyReturned;
            if (itemReq.getQuantity() > eligible) {
                throw new BadRequestException("Cannot return " + itemReq.getQuantity() + " of '" + saleItem.getProduct().getName()
                        + "': only " + eligible + " remaining eligible for return (of " + saleItem.getQuantity()
                        + " sold, " + alreadyReturned + " already returned/pending)");
            }

            BigDecimal qty = BigDecimal.valueOf(itemReq.getQuantity());
            BigDecimal soldQty = BigDecimal.valueOf(saleItem.getQuantity());
            BigDecimal itemTaxable = proportion(saleItem.getTaxableAmount(), qty, soldQty);
            BigDecimal itemDiscount = proportion(saleItem.getDiscount(), qty, soldQty);
            BigDecimal itemCgst = proportion(saleItem.getCgstAmount(), qty, soldQty);
            BigDecimal itemSgst = proportion(saleItem.getSgstAmount(), qty, soldQty);
            BigDecimal itemIgst = proportion(saleItem.getIgstAmount(), qty, soldQty);
            BigDecimal itemTotal = itemTaxable.add(itemCgst).add(itemSgst).add(itemIgst);

            note.addItem(CreditNoteItem.builder()
                    .saleItem(saleItem)
                    .product(saleItem.getProduct())
                    .quantity(itemReq.getQuantity())
                    .rate(saleItem.getSellingPrice())
                    .discount(itemDiscount)
                    .taxableAmount(itemTaxable)
                    .gstPercent(saleItem.getGstPercent())
                    .cgstAmount(itemCgst)
                    .sgstAmount(itemSgst)
                    .igstAmount(itemIgst)
                    .total(itemTotal)
                    .build());

            totalTaxable = totalTaxable.add(itemTaxable);
            totalCgst = totalCgst.add(itemCgst);
            totalSgst = totalSgst.add(itemSgst);
            totalIgst = totalIgst.add(itemIgst);
        }

        BigDecimal totalTax = totalCgst.add(totalSgst).add(totalIgst);
        note.setTaxableAmount(totalTaxable);
        note.setCgstAmount(totalCgst);
        note.setSgstAmount(totalSgst);
        note.setIgstAmount(totalIgst);
        note.setTotalTax(totalTax);
        note.setTotalAmount(totalTaxable.add(totalTax));

        CreditNote saved = creditNoteRepository.save(note);
        saved.setVoucherNumber(voucherNumberService.next(VoucherDocType.CREDIT_NOTE, request.getNoteDate()));
        saved = creditNoteRepository.save(saved);

        if (request.isPost()) {
            return post(saved.getId());
        }
        return CreditNoteResponse.fromEntity(saved);
    }

    @Transactional
    public CreditNoteResponse post(Long id) {
        CreditNote note = findOrThrow(id);
        if (note.getStatus() != NoteStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT credit note can be posted (current status: " + note.getStatus() + ")");
        }
        if (note.getSourceSale().getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("The source sale " + note.getSourceSale().getInvoiceNumber() + " has been cancelled since this note was created");
        }

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(SystemAccountCode.SALES, note.getTaxableAmount()));
        addIfPositive(lines, SystemAccountCode.OUTPUT_CGST, note.getCgstAmount(), true);
        addIfPositive(lines, SystemAccountCode.OUTPUT_SGST, note.getSgstAmount(), true);
        addIfPositive(lines, SystemAccountCode.OUTPUT_IGST, note.getIgstAmount(), true);
        lines.add(JournalLine.credit(SystemAccountCode.CUSTOMER_RECEIVABLE, note.getTotalAmount(),
                AccountingPartyType.CUSTOMER, note.getCustomer().getId()));

        accountingService.postJournal(VoucherType.CREDIT_NOTE, note.getId(), note.getVoucherNumber(),
                note.getNoteDate(), "Credit Note " + note.getVoucherNumber(), lines);

        if (note.getStockImpact() == StockImpactType.STOCK_RETURN) {
            for (CreditNoteItem item : note.getItems()) {
                inventoryService.applyMovement(item.getProduct().getId(), item.getQuantity(), com.storehub.entity.StockMovementType.SALES_RETURN,
                        ReferenceType.CREDIT_NOTE, note.getId(), "Sales return: Credit Note " + note.getVoucherNumber());
            }
        }

        ledgerService.recordCreditNoteEntry(note);

        note.setStatus(NoteStatus.POSTED);
        note.setPostedBy(SecurityUtil.currentUsername());
        note.setPostedAt(LocalDateTime.now());

        // Must run after the status flip: GstReportingEligibility checks status == POSTED.
        gstTransactionSyncService.syncCreditNote(note);
        return CreditNoteResponse.fromEntity(creditNoteRepository.save(note));
    }

    /** Idempotent: cancelling an already-CANCELLED note is a no-op that returns its current state. */
    @Transactional
    public CreditNoteResponse cancel(Long id) {
        CreditNote note = findOrThrow(id);
        if (note.getStatus() == NoteStatus.CANCELLED) {
            return CreditNoteResponse.fromEntity(note);
        }

        String reason = "Credit Note cancelled: " + note.getVoucherNumber();
        if (note.getStatus() == NoteStatus.POSTED) {
            accountingService.reverseJournal(VoucherType.CREDIT_NOTE, note.getId(), reason);
            if (note.getStockImpact() == StockImpactType.STOCK_RETURN) {
                for (CreditNoteItem item : note.getItems()) {
                    inventoryService.applyMovement(item.getProduct().getId(), -item.getQuantity(), com.storehub.entity.StockMovementType.ADJUSTMENT,
                            ReferenceType.CREDIT_NOTE, note.getId(), reason);
                }
            }
            ledgerService.reverseCreditNoteEntry(note, reason);
            gstTransactionSyncService.reverseCreditNote(note);
        }

        note.setStatus(NoteStatus.CANCELLED);
        note.setCancelledBy(SecurityUtil.currentUsername());
        note.setCancelledAt(LocalDateTime.now());
        return CreditNoteResponse.fromEntity(creditNoteRepository.save(note));
    }

    private BigDecimal proportion(BigDecimal totalForLine, BigDecimal returnedQty, BigDecimal soldQty) {
        if (totalForLine == null || totalForLine.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalForLine.multiply(returnedQty).divide(soldQty, 2, RoundingMode.HALF_UP);
    }

    private void addIfPositive(List<JournalLine> lines, SystemAccountCode account, BigDecimal amount, boolean debit) {
        if (amount != null && amount.signum() > 0) {
            lines.add(debit ? JournalLine.debit(account, amount) : JournalLine.credit(account, amount));
        }
    }

    private CreditNote findOrThrow(Long id) {
        return creditNoteRepository.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
    }
}
