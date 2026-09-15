package com.storehub.service;

import com.storehub.dto.DebitNoteCreateRequest;
import com.storehub.dto.DebitNoteItemRequest;
import com.storehub.dto.DebitNoteResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.DebitNote;
import com.storehub.entity.DebitNoteItem;
import com.storehub.entity.NoteStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseItem;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockImpactType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.NoteNotFoundException;
import com.storehub.exception.PurchaseNotFoundException;
import com.storehub.repository.DebitNoteItemRepository;
import com.storehub.repository.DebitNoteRepository;
import com.storehub.repository.PurchaseItemRepository;
import com.storehub.repository.PurchaseRepository;
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

/** Purchase Debit Note lifecycle — the Purchase-side mirror of {@link CreditNoteService}; see its Javadoc. */
@Service
@RequiredArgsConstructor
public class DebitNoteService {

    private final DebitNoteRepository debitNoteRepository;
    private final DebitNoteItemRepository debitNoteItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final LedgerService ledgerService;
    private final GstTransactionSyncService gstTransactionSyncService;

    @Transactional(readOnly = true)
    public PagedResponse<DebitNoteResponse> search(String search, NoteStatus status, LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<DebitNoteResponse> result = debitNoteRepository.search(search, status, fromDate, toDate, pageable)
                .map(DebitNoteResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public DebitNoteResponse getById(Long id) {
        return DebitNoteResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public DebitNoteResponse create(DebitNoteCreateRequest request) {
        Purchase purchase = purchaseRepository.findById(request.getSourcePurchaseId())
                .orElseThrow(() -> new PurchaseNotFoundException(request.getSourcePurchaseId()));
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("Cannot create a debit note against a cancelled purchase");
        }
        if (purchase.getStatus() == PurchaseStatus.DRAFT) {
            throw new BadRequestException("Cannot create a debit note against a purchase that has not been posted");
        }

        DebitNote note = DebitNote.builder()
                .noteType(request.getNoteType())
                .sourcePurchase(purchase)
                .supplier(purchase.getSupplier())
                .noteDate(request.getNoteDate())
                .financialYearId(financialYearService.resolveForDate(request.getNoteDate()).getId())
                .reason(request.getReason())
                .gstType(purchase.getGstType())
                .stockImpact(request.getStockImpact())
                .gstReportingApplicable(purchase.getGstReportingApplicable())
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
        for (DebitNoteItemRequest itemReq : request.getItems()) {
            PurchaseItem purchaseItem = purchaseItemRepository.findById(itemReq.getPurchaseItemId())
                    .orElseThrow(() -> new BadRequestException("Purchase item not found: " + itemReq.getPurchaseItemId()));
            if (!purchaseItem.getPurchase().getId().equals(purchase.getId())) {
                throw new BadRequestException("Purchase item " + purchaseItem.getId() + " does not belong to purchase " + purchase.getPurchaseNumber());
            }
            if (itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than 0");
            }

            int alreadyReturned = debitNoteItemRepository.sumReturnedQuantity(purchaseItem.getId());
            int eligible = purchaseItem.getQuantity() - alreadyReturned;
            if (itemReq.getQuantity() > eligible) {
                throw new BadRequestException("Cannot return " + itemReq.getQuantity() + " of '" + purchaseItem.getProduct().getName()
                        + "': only " + eligible + " remaining eligible for return (of " + purchaseItem.getQuantity()
                        + " purchased, " + alreadyReturned + " already returned/pending)");
            }

            BigDecimal qty = BigDecimal.valueOf(itemReq.getQuantity());
            BigDecimal purchasedQty = BigDecimal.valueOf(purchaseItem.getQuantity());
            BigDecimal itemTaxable = proportion(purchaseItem.getTaxableAmount(), qty, purchasedQty);
            BigDecimal itemDiscount = proportion(purchaseItem.getDiscount(), qty, purchasedQty);
            BigDecimal itemCgst = proportion(purchaseItem.getCgstAmount(), qty, purchasedQty);
            BigDecimal itemSgst = proportion(purchaseItem.getSgstAmount(), qty, purchasedQty);
            BigDecimal itemIgst = proportion(purchaseItem.getIgstAmount(), qty, purchasedQty);
            BigDecimal itemTotal = itemTaxable.add(itemCgst).add(itemSgst).add(itemIgst);

            note.addItem(DebitNoteItem.builder()
                    .purchaseItem(purchaseItem)
                    .product(purchaseItem.getProduct())
                    .quantity(itemReq.getQuantity())
                    .rate(purchaseItem.getPurchasePrice())
                    .discount(itemDiscount)
                    .taxableAmount(itemTaxable)
                    .gstPercent(purchaseItem.getGstPercent())
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

        DebitNote saved = debitNoteRepository.save(note);
        saved.setVoucherNumber(voucherNumberService.next(VoucherDocType.DEBIT_NOTE, request.getNoteDate()));
        saved = debitNoteRepository.save(saved);

        if (request.isPost()) {
            return post(saved.getId());
        }
        return DebitNoteResponse.fromEntity(saved);
    }

    @Transactional
    public DebitNoteResponse post(Long id) {
        DebitNote note = findOrThrow(id);
        if (note.getStatus() != NoteStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT debit note can be posted (current status: " + note.getStatus() + ")");
        }
        if (note.getSourcePurchase().getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("The source purchase " + note.getSourcePurchase().getPurchaseNumber() + " has been cancelled since this note was created");
        }

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(SystemAccountCode.SUPPLIER_PAYABLE, note.getTotalAmount(),
                AccountingPartyType.SUPPLIER, note.getSupplier().getId()));
        lines.add(JournalLine.credit(SystemAccountCode.PURCHASE, note.getTaxableAmount()));
        addIfPositive(lines, SystemAccountCode.INPUT_CGST, note.getCgstAmount(), false);
        addIfPositive(lines, SystemAccountCode.INPUT_SGST, note.getSgstAmount(), false);
        addIfPositive(lines, SystemAccountCode.INPUT_IGST, note.getIgstAmount(), false);

        accountingService.postJournal(VoucherType.DEBIT_NOTE, note.getId(), note.getVoucherNumber(),
                note.getNoteDate(), "Debit Note " + note.getVoucherNumber(), lines);

        if (note.getStockImpact() == StockImpactType.STOCK_RETURN) {
            for (DebitNoteItem item : note.getItems()) {
                inventoryService.applyMovement(item.getProduct().getId(), -item.getQuantity(), com.storehub.entity.StockMovementType.PURCHASE_RETURN,
                        ReferenceType.DEBIT_NOTE, note.getId(), "Purchase return: Debit Note " + note.getVoucherNumber());
            }
        }

        ledgerService.recordDebitNoteEntry(note);

        note.setStatus(NoteStatus.POSTED);
        note.setPostedBy(SecurityUtil.currentUsername());
        note.setPostedAt(LocalDateTime.now());

        // Must run after the status flip: GstReportingEligibility checks status == POSTED.
        gstTransactionSyncService.syncDebitNote(note);
        return DebitNoteResponse.fromEntity(debitNoteRepository.save(note));
    }

    @Transactional
    public DebitNoteResponse cancel(Long id) {
        DebitNote note = findOrThrow(id);
        if (note.getStatus() == NoteStatus.CANCELLED) {
            return DebitNoteResponse.fromEntity(note);
        }

        String reason = "Debit Note cancelled: " + note.getVoucherNumber();
        if (note.getStatus() == NoteStatus.POSTED) {
            accountingService.reverseJournal(VoucherType.DEBIT_NOTE, note.getId(), reason);
            if (note.getStockImpact() == StockImpactType.STOCK_RETURN) {
                for (DebitNoteItem item : note.getItems()) {
                    inventoryService.applyMovement(item.getProduct().getId(), item.getQuantity(), com.storehub.entity.StockMovementType.ADJUSTMENT,
                            ReferenceType.DEBIT_NOTE, note.getId(), reason);
                }
            }
            ledgerService.reverseDebitNoteEntry(note, reason);
            gstTransactionSyncService.reverseDebitNote(note);
        }

        note.setStatus(NoteStatus.CANCELLED);
        note.setCancelledBy(SecurityUtil.currentUsername());
        note.setCancelledAt(LocalDateTime.now());
        return DebitNoteResponse.fromEntity(debitNoteRepository.save(note));
    }

    private BigDecimal proportion(BigDecimal totalForLine, BigDecimal returnedQty, BigDecimal purchasedQty) {
        if (totalForLine == null || totalForLine.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalForLine.multiply(returnedQty).divide(purchasedQty, 2, RoundingMode.HALF_UP);
    }

    private void addIfPositive(List<JournalLine> lines, SystemAccountCode account, BigDecimal amount, boolean debit) {
        if (amount != null && amount.signum() > 0) {
            lines.add(debit ? JournalLine.debit(account, amount) : JournalLine.credit(account, amount));
        }
    }

    private DebitNote findOrThrow(Long id) {
        return debitNoteRepository.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
    }
}
