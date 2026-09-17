package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockTransferItemRequest;
import com.storehub.dto.StockTransferRequest;
import com.storehub.dto.StockTransferResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockTransfer;
import com.storehub.entity.StockTransferItem;
import com.storehub.entity.StockTransferStatus;
import com.storehub.entity.Store;
import com.storehub.entity.StoreStatus;
import com.storehub.entity.User;
import com.storehub.entity.VoucherDocType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.exception.StockTransferNotFoundException;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.StockTransferRepository;
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

/**
 * Store-to-store stock transfer workflow (Multi-Store spec section 20):
 * DRAFT -&gt; APPROVED -&gt; DISPATCHED -&gt; RECEIVED, or CANCELLED from DRAFT/APPROVED
 * (before any stock has actually moved — cancelling after DISPATCHED would need
 * a receive-back movement, which is out of scope for this workflow and is
 * rejected with a clear message instead). Stock only ever moves through the
 * same centralized {@link InventoryService#applyMovement} every other
 * store-attributed voucher uses: a TRANSFER_OUT deduction from the source
 * store at DISPATCHED, and a TRANSFER_IN addition to the destination store
 * at RECEIVED — never at DRAFT/APPROVED, which are purely an authorization
 * stage with no stock impact.
 */
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final StoreService storeService;
    private final StoreAccessService storeAccessService;
    private final InventoryService inventoryService;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PagedResponse<StockTransferResponse> search(String search, StockTransferStatus status, LocalDate fromDate,
                                                         LocalDate toDate, Long storeId, int page, int size) {
        Long resolvedStoreId = storeAccessService.resolveViewableStoreId(SecurityUtil.currentUserOrNull(), storeId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockTransferResponse> result = stockTransferRepository.search(search, status, fromDate, toDate, resolvedStoreId, pageable)
                .map(StockTransferResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    /** Visible to a user with access to EITHER the source or destination store (Multi-Store spec section 14). */
    @Transactional(readOnly = true)
    public StockTransferResponse getById(Long id) {
        StockTransfer transfer = findOrThrow(id);
        assertEitherStoreAccess(transfer);
        return StockTransferResponse.fromEntity(transfer);
    }

    @Transactional
    public StockTransferResponse create(StockTransferRequest request) {
        User currentUser = SecurityUtil.currentUserOrNull();
        storeAccessService.assertStoreAccess(currentUser, request.getFromStoreId());

        if (request.getFromStoreId().equals(request.getToStoreId())) {
            throw new BadRequestException("Source and destination store must be different");
        }
        Store fromStore = resolveActiveStore(request.getFromStoreId());
        Store toStore = resolveActiveStore(request.getToStoreId());

        StockTransfer transfer = StockTransfer.builder()
                .transferDate(request.getTransferDate())
                .fromStore(fromStore)
                .toStore(toStore)
                .remarks(request.getRemarks())
                .status(StockTransferStatus.DRAFT)
                .financialYearId(financialYearService.resolveForDate(request.getTransferDate()).getId())
                .createdBy(SecurityUtil.currentUsername())
                .build();

        for (StockTransferItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));
            if (product.getStatus() == ProductStatus.INACTIVE) {
                throw new BadRequestException("Product '" + product.getName() + "' is inactive and cannot be transferred");
            }
            transfer.addItem(StockTransferItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .notes(itemRequest.getNotes())
                    .build());
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        saved.setTransferNumber(voucherNumberService.next(VoucherDocType.STOCK_TRANSFER, request.getTransferDate()));
        saved = stockTransferRepository.save(saved);

        auditService.log(AuditAction.CREATE, "INVENTORY", "StockTransfer", saved.getId(), saved.getTransferNumber(),
                null, null, "Stock transfer " + saved.getTransferNumber() + " created: " + fromStore.getStoreCode()
                        + " -> " + toStore.getStoreCode(), fromStore.getId());

        return StockTransferResponse.fromEntity(saved);
    }

    @Transactional
    public StockTransferResponse approve(Long id) {
        StockTransfer transfer = findOrThrow(id);
        storeAccessService.assertStoreAccess(SecurityUtil.currentUserOrNull(), transfer.getFromStore().getId());
        requireStatus(transfer, StockTransferStatus.DRAFT, "approved");

        transfer.setStatus(StockTransferStatus.APPROVED);
        transfer.setApprovedBy(SecurityUtil.currentUsername());
        transfer.setApprovedAt(LocalDateTime.now());
        StockTransfer saved = stockTransferRepository.save(transfer);

        auditService.log(AuditAction.UPDATE, "INVENTORY", "StockTransfer", saved.getId(), saved.getTransferNumber(),
                null, null, "Stock transfer " + saved.getTransferNumber() + " approved", saved.getFromStore().getId());
        return StockTransferResponse.fromEntity(saved);
    }

    /** Deducts stock from the source store — the point stock actually leaves the origin. */
    @Transactional
    public StockTransferResponse dispatch(Long id) {
        StockTransfer transfer = findOrThrow(id);
        storeAccessService.assertStoreAccess(SecurityUtil.currentUserOrNull(), transfer.getFromStore().getId());
        requireStatus(transfer, StockTransferStatus.APPROVED, "dispatched");

        for (StockTransferItem item : transfer.getItems()) {
            inventoryService.applyMovement(item.getProduct().getId(), transfer.getFromStore().getId(), -item.getQuantity(),
                    StockMovementType.TRANSFER_OUT, ReferenceType.STOCK_TRANSFER, transfer.getId(),
                    "Stock transfer " + transfer.getTransferNumber() + " dispatched to " + transfer.getToStore().getStoreCode());
        }

        transfer.setStatus(StockTransferStatus.DISPATCHED);
        transfer.setDispatchedBy(SecurityUtil.currentUsername());
        transfer.setDispatchedAt(LocalDateTime.now());
        StockTransfer saved = stockTransferRepository.save(transfer);

        auditService.log(AuditAction.UPDATE, "INVENTORY", "StockTransfer", saved.getId(), saved.getTransferNumber(),
                null, null, "Stock transfer " + saved.getTransferNumber() + " dispatched: stock deducted from "
                        + saved.getFromStore().getStoreCode(), saved.getFromStore().getId());
        return StockTransferResponse.fromEntity(saved);
    }

    /** Adds stock to the destination store — the point stock actually lands at the destination. */
    @Transactional
    public StockTransferResponse receive(Long id) {
        StockTransfer transfer = findOrThrow(id);
        storeAccessService.assertStoreAccess(SecurityUtil.currentUserOrNull(), transfer.getToStore().getId());
        requireStatus(transfer, StockTransferStatus.DISPATCHED, "received");

        for (StockTransferItem item : transfer.getItems()) {
            inventoryService.applyMovement(item.getProduct().getId(), transfer.getToStore().getId(), item.getQuantity(),
                    StockMovementType.TRANSFER_IN, ReferenceType.STOCK_TRANSFER, transfer.getId(),
                    "Stock transfer " + transfer.getTransferNumber() + " received from " + transfer.getFromStore().getStoreCode());
        }

        transfer.setStatus(StockTransferStatus.RECEIVED);
        transfer.setReceivedBy(SecurityUtil.currentUsername());
        transfer.setReceivedAt(LocalDateTime.now());
        StockTransfer saved = stockTransferRepository.save(transfer);

        auditService.log(AuditAction.UPDATE, "INVENTORY", "StockTransfer", saved.getId(), saved.getTransferNumber(),
                null, null, "Stock transfer " + saved.getTransferNumber() + " received: stock added to "
                        + saved.getToStore().getStoreCode(), saved.getToStore().getId());
        return StockTransferResponse.fromEntity(saved);
    }

    /** Only possible before any stock has moved (DRAFT/APPROVED) — a DISPATCHED transfer must instead be received then reversed via a return transfer. */
    @Transactional
    public StockTransferResponse cancel(Long id) {
        StockTransfer transfer = findOrThrow(id);
        storeAccessService.assertStoreAccess(SecurityUtil.currentUserOrNull(), transfer.getFromStore().getId());

        if (transfer.getStatus() != StockTransferStatus.DRAFT && transfer.getStatus() != StockTransferStatus.APPROVED) {
            throw new BadRequestException("Only a DRAFT or APPROVED stock transfer can be cancelled (current status: "
                    + transfer.getStatus() + "); a DISPATCHED/RECEIVED transfer already moved stock and cannot be cancelled");
        }

        String reason = "Stock transfer cancelled: " + transfer.getTransferNumber();
        transfer.setStatus(StockTransferStatus.CANCELLED);
        transfer.setCancelledBy(SecurityUtil.currentUsername());
        transfer.setCancelledAt(LocalDateTime.now());
        transfer.setCancellationReason(reason);
        StockTransfer saved = stockTransferRepository.save(transfer);

        auditService.log(AuditAction.CANCEL, "INVENTORY", "StockTransfer", saved.getId(), saved.getTransferNumber(),
                null, null, reason, saved.getFromStore().getId());
        return StockTransferResponse.fromEntity(saved);
    }

    private void requireStatus(StockTransfer transfer, StockTransferStatus required, String actionPastTense) {
        if (transfer.getStatus() != required) {
            throw new BadRequestException("Only a " + required + " stock transfer can be " + actionPastTense
                    + " (current status: " + transfer.getStatus() + ")");
        }
    }

    private void assertEitherStoreAccess(StockTransfer transfer) {
        User currentUser = SecurityUtil.currentUserOrNull();
        boolean canSeeFrom = storeAccessService.hasStoreAccess(currentUser, transfer.getFromStore().getId());
        boolean canSeeTo = storeAccessService.hasStoreAccess(currentUser, transfer.getToStore().getId());
        if (!canSeeFrom && !canSeeTo) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have access to this stock transfer");
        }
    }

    private Store resolveActiveStore(Long storeId) {
        Store store = storeService.findOrThrow(storeId);
        if (store.getStatus() == StoreStatus.INACTIVE) {
            throw new BadRequestException("Store '" + store.getStoreName() + "' is inactive and cannot be used in a stock transfer");
        }
        return store;
    }

    private StockTransfer findOrThrow(Long id) {
        return stockTransferRepository.findById(id).orElseThrow(() -> new StockTransferNotFoundException(id));
    }
}
