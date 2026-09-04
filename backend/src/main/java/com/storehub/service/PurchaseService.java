package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.PurchaseUpdateRequest;
import com.storehub.entity.GstType;
import com.storehub.entity.PaymentAllocation;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseItem;
import com.storehub.entity.PurchaseOrder;
import com.storehub.entity.PurchaseOrderItem;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.TaxMode;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.PurchaseNotFoundException;
import com.storehub.repository.PaymentAllocationRepository;
import com.storehub.repository.PurchaseOrderItemRepository;
import com.storehub.repository.PurchaseRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final PurchaseOrderService purchaseOrderService;
    private final LedgerService ledgerService;
    private final PaymentService paymentService;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public PagedResponse<PurchaseResponse> getPurchases(String search, PaymentStatus paymentStatus,
                                                          PurchaseStatus status, LocalDate fromDate, LocalDate toDate,
                                                          int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseResponse> result = purchaseRepository
                .search(search, paymentStatus, status, fromDate, toDate, pageable)
                .map(PurchaseResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public PurchaseResponse getPurchaseById(Long id) {
        Purchase purchase = findPurchaseOrThrow(id);
        boolean hasPayments = !paymentAllocationRepository.findByPurchaseId(id).isEmpty();
        return PurchaseResponse.fromEntity(purchase, hasPayments);
    }

    @Transactional
    public PurchaseResponse createPurchase(PurchaseCreateRequest request) {
        if (request.getGstType() == GstType.GST && request.getTaxMode() == null) {
            throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required for a GST purchase");
        }

        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());
        if (supplier.getStatus() == SupplierStatus.INACTIVE) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchases");
        }

        PurchaseOrder purchaseOrder = request.getPurchaseOrderId() != null
                ? purchaseOrderService.findOrThrow(request.getPurchaseOrderId())
                : null;

        Purchase purchase = Purchase.builder()
                .supplier(supplier)
                .purchaseDate(request.getPurchaseDate())
                .status(PurchaseStatus.COMPLETED)
                .notes(request.getNotes())
                .gstType(request.getGstType())
                .taxMode(request.getGstType() == GstType.GST ? request.getTaxMode() : null)
                .supplierPhone(request.getSupplierPhone())
                .supplierGstin(request.getSupplierGstin())
                .billingAddress(request.getBillingAddress())
                .shippingAddress(request.getShippingAddress())
                .paymentMode(request.getPaymentMode())
                .purchaseOrder(purchaseOrder)
                .build();

        applyItems(purchase, request.getItems(), Collections.emptySet());
        applyPayment(purchase, request.getPaidAmount());

        Purchase saved = purchaseRepository.save(purchase);
        saved.setPurchaseNumber(String.format("PUR-%06d", saved.getId()));
        saved = purchaseRepository.save(saved);

        addStock(saved, saved.getItems());
        ledgerService.recordPurchaseCredit(saved);
        ledgerService.recordInputGstEntry(saved);
        consumeOrderQuantities(saved.getItems(), 1);

        if (saved.getPaidAmount().signum() > 0) {
            paymentService.createSystemPaymentForPurchase(saved);
        }

        boolean hasPayments = !paymentAllocationRepository.findByPurchaseId(saved.getId()).isEmpty();
        return PurchaseResponse.fromEntity(saved, hasPayments);
    }

    @Transactional
    public PurchaseResponse updatePurchase(Long id, PurchaseUpdateRequest request) {
        Purchase purchase = findPurchaseOrThrow(id);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("A cancelled purchase cannot be edited");
        }
        if (request.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("Use the delete action to cancel and reverse a purchase");
        }
        if (!paymentAllocationRepository.findByPurchaseId(id).isEmpty()) {
            throw new BadRequestException("This purchase already has a payment recorded against it. "
                    + "Delete the payment first, or adjust payment via Payment Entry instead of editing the bill.");
        }
        if (request.getGstType() == GstType.GST && request.getTaxMode() == null) {
            throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required for a GST purchase");
        }

        List<PurchaseItem> oldItems = new ArrayList<>(purchase.getItems());
        Set<Long> allowedInactiveProductIds = oldItems.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        boolean oldWasCompleted = purchase.getStatus() == PurchaseStatus.COMPLETED;
        if (oldWasCompleted) {
            restoreStock(purchase, oldItems);
        }
        ledgerService.reversePurchaseCredit(purchase, "Purchase revised: " + purchase.getPurchaseNumber());
        ledgerService.reverseInputGstEntry(purchase);
        consumeOrderQuantities(oldItems, -1);

        Long oldSupplierId = purchase.getSupplier().getId();
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());
        if (supplier.getStatus() == SupplierStatus.INACTIVE && !supplier.getId().equals(oldSupplierId)) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchases");
        }

        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.getPurchaseDate());
        purchase.setNotes(request.getNotes());
        purchase.setGstType(request.getGstType());
        purchase.setTaxMode(request.getGstType() == GstType.GST ? request.getTaxMode() : null);
        purchase.setSupplierPhone(request.getSupplierPhone());
        purchase.setSupplierGstin(request.getSupplierGstin());
        purchase.setBillingAddress(request.getBillingAddress());
        purchase.setShippingAddress(request.getShippingAddress());
        purchase.setPaymentMode(request.getPaymentMode());

        purchase.clearItems();
        applyItems(purchase, request.getItems(), allowedInactiveProductIds);
        applyPayment(purchase, request.getPaidAmount());

        if (request.getStatus() == PurchaseStatus.COMPLETED) {
            addStock(purchase, purchase.getItems());
        }
        purchase.setStatus(request.getStatus());

        Purchase saved = purchaseRepository.save(purchase);
        ledgerService.recordPurchaseCredit(saved);
        ledgerService.recordInputGstEntry(saved);
        consumeOrderQuantities(saved.getItems(), 1);

        if (saved.getPaidAmount().signum() > 0) {
            paymentService.createSystemPaymentForPurchase(saved);
        }

        boolean hasPaymentsNow = !paymentAllocationRepository.findByPurchaseId(saved.getId()).isEmpty();
        return PurchaseResponse.fromEntity(saved, hasPaymentsNow);
    }

    /** Soft reversal: keeps the record for history but undoes stock/ledger/GST/order effects. */
    @Transactional
    public PurchaseResponse cancelPurchase(Long id) {
        Purchase purchase = findPurchaseOrThrow(id);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("This purchase is already cancelled");
        }
        guardNoManualPayments(purchase);

        reversePurchaseEffects(purchase, "Purchase cancelled: " + purchase.getPurchaseNumber());
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setPayableAmount(BigDecimal.ZERO);

        return PurchaseResponse.fromEntity(purchaseRepository.save(purchase), false);
    }

    /** Hard delete: fully reverses every side effect, then removes the record. */
    @Transactional
    public void deletePurchase(Long id) {
        Purchase purchase = findPurchaseOrThrow(id);
        guardNoManualPayments(purchase);

        reversePurchaseEffects(purchase, "Purchase deleted: " + purchase.getPurchaseNumber());
        purchaseRepository.delete(purchase);
    }

    private void guardNoManualPayments(Purchase purchase) {
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPurchaseId(purchase.getId());
        boolean hasManualPayment = allocations.stream().anyMatch(a -> !a.getPayment().isSystemGenerated());
        if (hasManualPayment) {
            throw new BadRequestException("This purchase has one or more payments recorded against it. "
                    + "Delete those payments first before reversing the purchase.");
        }
    }

    private void reversePurchaseEffects(Purchase purchase, String reason) {
        if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
            restoreStock(purchase, purchase.getItems());
        }
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPurchaseId(purchase.getId());
        for (PaymentAllocation allocation : allocations) {
            paymentService.deleteSystemPayment(allocation.getPayment().getId());
        }
        ledgerService.reversePurchaseCredit(purchase, reason);
        ledgerService.reverseInputGstEntry(purchase);
        consumeOrderQuantities(purchase.getItems(), -1);
    }

    private void consumeOrderQuantities(List<PurchaseItem> items, int sign) {
        for (PurchaseItem item : items) {
            if (item.getPurchaseOrderItem() != null) {
                purchaseOrderService.applyReceivedQuantityDelta(item.getPurchaseOrderItem().getId(), sign * item.getQuantity());
            }
        }
    }

    private void applyPayment(Purchase purchase, BigDecimal paidAmount) {
        if (paidAmount.compareTo(purchase.getTotalAmount()) > 0) {
            throw new BadRequestException("Paid amount (" + paidAmount + ") cannot exceed the payable amount (" + purchase.getTotalAmount() + ")");
        }
        purchase.setPaidAmount(paidAmount);
        purchase.setPayableAmount(purchase.getTotalAmount().subtract(paidAmount));
        purchase.setPaymentStatus(derivePaymentStatus(paidAmount, purchase.getTotalAmount()));
    }

    private PaymentStatus derivePaymentStatus(BigDecimal paid, BigDecimal total) {
        if (total.signum() <= 0 || paid.compareTo(total) >= 0) {
            return PaymentStatus.PAID;
        }
        if (paid.signum() <= 0) {
            return PaymentStatus.UNPAID;
        }
        return PaymentStatus.PARTIAL;
    }

    private void applyItems(Purchase purchase, List<PurchaseItemRequest> itemRequests, Set<Long> allowedInactiveProductIds) {
        Set<Long> seenProductIds = new HashSet<>();
        boolean isGst = purchase.getGstType() == GstType.GST;
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (PurchaseItemRequest itemRequest : itemRequests) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("The same product cannot be added more than once in a purchase");
            }

            Product product = productService.findProductOrThrow(itemRequest.getProductId());

            if (product.getStatus() == ProductStatus.INACTIVE
                    && !allowedInactiveProductIds.contains(product.getId())) {
                throw new BadRequestException("Product '" + product.getName() + "' is inactive and cannot be used in new purchases");
            }

            PurchaseOrderItem orderItem = null;
            if (itemRequest.getPurchaseOrderItemId() != null) {
                orderItem = purchaseOrderItemRepository.findById(itemRequest.getPurchaseOrderItemId())
                        .orElseThrow(() -> new BadRequestException("Purchase order item not found: " + itemRequest.getPurchaseOrderItemId()));
                if (itemRequest.getQuantity() > orderItem.getRemainingQuantity()) {
                    throw new BadRequestException("Cannot bill " + itemRequest.getQuantity() + " of '" + product.getName()
                            + "': only " + orderItem.getRemainingQuantity() + " remaining on the order");
                }
            }

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal taxableAmount = quantity.multiply(itemRequest.getPurchasePrice()).subtract(itemRequest.getDiscount());
            if (taxableAmount.signum() < 0) {
                throw new BadRequestException("Discount cannot exceed the item amount for product '" + product.getName() + "'");
            }

            BigDecimal gstPercent = isGst ? itemRequest.getGstPercent() : BigDecimal.ZERO;
            BigDecimal gstAmount = isGst
                    ? taxableAmount.multiply(gstPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal cgst = BigDecimal.ZERO;
            BigDecimal sgst = BigDecimal.ZERO;
            BigDecimal igst = BigDecimal.ZERO;
            if (isGst && gstAmount.signum() > 0) {
                if (purchase.getTaxMode() == TaxMode.INTER_STATE) {
                    igst = gstAmount;
                } else {
                    cgst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    sgst = gstAmount.subtract(cgst);
                }
            }

            BigDecimal subtotal = taxableAmount.add(gstAmount);

            PurchaseItem item = PurchaseItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .purchasePrice(itemRequest.getPurchasePrice())
                    .discount(itemRequest.getDiscount())
                    .tax(gstAmount)
                    .subtotal(subtotal)
                    .gstPercent(gstPercent)
                    .taxableAmount(taxableAmount)
                    .cgstAmount(cgst)
                    .sgstAmount(sgst)
                    .igstAmount(igst)
                    .purchaseOrderItem(orderItem)
                    .build();

            purchase.addItem(item);
            taxableTotal = taxableTotal.add(taxableAmount);
            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            igstTotal = igstTotal.add(igst);
            grandTotal = grandTotal.add(subtotal);
        }

        purchase.setTaxableAmount(taxableTotal);
        purchase.setCgstAmount(cgstTotal);
        purchase.setSgstAmount(sgstTotal);
        purchase.setIgstAmount(igstTotal);
        purchase.setTotalAmount(grandTotal);
    }

    private void restoreStock(Purchase purchase, List<PurchaseItem> items) {
        String reason = "Purchase reversed: " + purchase.getPurchaseNumber();
        for (PurchaseItem item : items) {
            inventoryService.applyMovement(item.getProduct().getId(), -item.getQuantity(),
                    StockMovementType.PURCHASE_CANCEL, ReferenceType.PURCHASE, purchase.getId(), reason);
        }
    }

    private void addStock(Purchase purchase, List<PurchaseItem> items) {
        String reason = "Purchase " + purchase.getPurchaseNumber();
        for (PurchaseItem item : items) {
            inventoryService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    StockMovementType.PURCHASE, ReferenceType.PURCHASE, purchase.getId(), reason);
        }
    }

    public Purchase findPurchaseOrThrow(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
    }
}
