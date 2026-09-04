package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.dto.SaleUpdateRequest;
import com.storehub.entity.Customer;
import com.storehub.entity.GstType;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReceiptAllocation;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleItem;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.SalesOrder;
import com.storehub.entity.SalesOrderItem;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.TaxMode;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.SaleNotFoundException;
import com.storehub.repository.ReceiptAllocationRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.SalesOrderItemRepository;
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
public class SaleService {

    private final SaleRepository saleRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final ProductService productService;
    private final CustomerService customerService;
    private final InventoryService inventoryService;
    private final SalesOrderService salesOrderService;
    private final LedgerService ledgerService;
    private final ReceiptService receiptService;
    private final ReceiptAllocationRepository receiptAllocationRepository;

    public PagedResponse<SaleResponse> getSales(String search, PaymentStatus paymentStatus,
                                                 SaleStatus status, LocalDate fromDate, LocalDate toDate,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SaleResponse> result = saleRepository
                .search(search, paymentStatus, status, fromDate, toDate, pageable)
                .map(SaleResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public SaleResponse getSaleById(Long id) {
        Sale sale = findSaleOrThrow(id);
        boolean hasReceipts = !receiptAllocationRepository.findBySaleId(id).isEmpty();
        return SaleResponse.fromEntity(sale, hasReceipts);
    }

    @Transactional
    public SaleResponse createSale(SaleCreateRequest request) {
        if (request.getGstType() == GstType.GST && request.getTaxMode() == null) {
            throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required for a GST sale");
        }

        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        SalesOrder salesOrder = request.getSalesOrderId() != null
                ? salesOrderService.findOrThrow(request.getSalesOrderId())
                : null;

        Sale sale = Sale.builder()
                .customer(customer)
                .saleDate(request.getSaleDate())
                .status(SaleStatus.COMPLETED)
                .notes(request.getNotes())
                .gstType(request.getGstType())
                .taxMode(request.getGstType() == GstType.GST ? request.getTaxMode() : null)
                .customerPhone(request.getCustomerPhone())
                .customerGstin(request.getCustomerGstin())
                .billingAddress(request.getBillingAddress())
                .shippingAddress(request.getShippingAddress())
                .paymentMode(request.getPaymentMode())
                .salesOrder(salesOrder)
                .build();

        applyItems(sale, request.getItems(), Collections.emptySet());
        applyPayment(sale, request.getPaidAmount());

        Sale saved = saleRepository.save(sale);
        saved.setInvoiceNumber(String.format("INV-%06d", saved.getId()));
        saved = saleRepository.save(saved);

        deductStock(saved, saved.getItems());
        ledgerService.recordSaleDebit(saved);
        ledgerService.recordGstEntry(saved);
        consumeOrderQuantities(saved.getItems(), 1);

        if (saved.getPaidAmount().signum() > 0) {
            if (saved.getCustomer() != null) {
                receiptService.createSystemReceiptForSale(saved);
            } else {
                ledgerService.recordCashEntryForSale(saved);
            }
        }

        boolean hasReceipts = !receiptAllocationRepository.findBySaleId(saved.getId()).isEmpty();
        return SaleResponse.fromEntity(saved, hasReceipts);
    }

    @Transactional
    public SaleResponse updateSale(Long id, SaleUpdateRequest request) {
        Sale sale = findSaleOrThrow(id);

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("A cancelled sale cannot be edited");
        }
        if (request.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("Use the delete action to cancel and reverse a sale");
        }
        if (!receiptAllocationRepository.findBySaleId(id).isEmpty()) {
            throw new BadRequestException("This sale already has a receipt recorded against it. "
                    + "Delete the receipt first, or adjust payment via Receipt Entry instead of editing the bill.");
        }
        if (request.getGstType() == GstType.GST && request.getTaxMode() == null) {
            throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required for a GST sale");
        }

        List<SaleItem> oldItems = new ArrayList<>(sale.getItems());
        Set<Long> allowedInactiveProductIds = oldItems.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        boolean oldWasCompleted = sale.getStatus() == SaleStatus.COMPLETED;
        if (oldWasCompleted) {
            restoreStock(sale, oldItems);
        }
        ledgerService.reverseSaleDebit(sale, "Sale revised: " + sale.getInvoiceNumber());
        ledgerService.reverseGstEntry(sale);
        if (sale.getCustomer() == null) {
            ledgerService.reverseCashEntryForSale(sale, "Sale revised: " + sale.getInvoiceNumber());
        }
        consumeOrderQuantities(oldItems, -1);

        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        sale.setCustomer(customer);
        sale.setSaleDate(request.getSaleDate());
        sale.setNotes(request.getNotes());
        sale.setGstType(request.getGstType());
        sale.setTaxMode(request.getGstType() == GstType.GST ? request.getTaxMode() : null);
        sale.setCustomerPhone(request.getCustomerPhone());
        sale.setCustomerGstin(request.getCustomerGstin());
        sale.setBillingAddress(request.getBillingAddress());
        sale.setShippingAddress(request.getShippingAddress());
        sale.setPaymentMode(request.getPaymentMode());

        sale.clearItems();
        applyItems(sale, request.getItems(), allowedInactiveProductIds);
        applyPayment(sale, request.getPaidAmount());

        if (request.getStatus() == SaleStatus.COMPLETED) {
            deductStock(sale, sale.getItems());
        }
        sale.setStatus(request.getStatus());

        Sale saved = saleRepository.save(sale);
        ledgerService.recordSaleDebit(saved);
        ledgerService.recordGstEntry(saved);
        consumeOrderQuantities(saved.getItems(), 1);

        if (saved.getPaidAmount().signum() > 0) {
            if (saved.getCustomer() != null) {
                receiptService.createSystemReceiptForSale(saved);
            } else {
                ledgerService.recordCashEntryForSale(saved);
            }
        }

        boolean hasReceiptsNow = !receiptAllocationRepository.findBySaleId(saved.getId()).isEmpty();
        return SaleResponse.fromEntity(saved, hasReceiptsNow);
    }

    /** Soft reversal: keeps the record for history but undoes stock/ledger/GST/order effects. */
    @Transactional
    public SaleResponse cancelSale(Long id) {
        Sale sale = findSaleOrThrow(id);

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("This sale is already cancelled");
        }
        guardNoManualReceipts(sale);

        reverseSaleEffects(sale, "Sale cancelled: " + sale.getInvoiceNumber());
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setDueAmount(BigDecimal.ZERO);

        return SaleResponse.fromEntity(saleRepository.save(sale), false);
    }

    /** Hard delete: fully reverses every side effect, then removes the record. */
    @Transactional
    public void deleteSale(Long id) {
        Sale sale = findSaleOrThrow(id);
        guardNoManualReceipts(sale);

        reverseSaleEffects(sale, "Sale deleted: " + sale.getInvoiceNumber());
        saleRepository.delete(sale);
    }

    private void guardNoManualReceipts(Sale sale) {
        List<ReceiptAllocation> allocations = receiptAllocationRepository.findBySaleId(sale.getId());
        boolean hasManualReceipt = allocations.stream().anyMatch(a -> !a.getReceipt().isSystemGenerated());
        if (hasManualReceipt) {
            throw new BadRequestException("This sale has one or more receipts recorded against it. "
                    + "Delete those receipts first before reversing the sale.");
        }
    }

    private void reverseSaleEffects(Sale sale, String reason) {
        if (sale.getStatus() == SaleStatus.COMPLETED) {
            restoreStock(sale, sale.getItems());
        }
        List<ReceiptAllocation> allocations = receiptAllocationRepository.findBySaleId(sale.getId());
        for (ReceiptAllocation allocation : allocations) {
            receiptService.deleteSystemReceipt(allocation.getReceipt().getId());
        }
        if (allocations.isEmpty() && sale.getCustomer() == null) {
            ledgerService.reverseCashEntryForSale(sale, reason);
        }
        ledgerService.reverseSaleDebit(sale, reason);
        ledgerService.reverseGstEntry(sale);
        consumeOrderQuantities(sale.getItems(), -1);
    }

    private void consumeOrderQuantities(List<SaleItem> items, int sign) {
        for (SaleItem item : items) {
            if (item.getSalesOrderItem() != null) {
                salesOrderService.applyBilledQuantityDelta(item.getSalesOrderItem().getId(), sign * item.getQuantity());
            }
        }
    }

    private void applyPayment(Sale sale, BigDecimal paidAmount) {
        if (paidAmount.compareTo(sale.getTotalAmount()) > 0) {
            throw new BadRequestException("Paid amount (" + paidAmount + ") cannot exceed the bill total (" + sale.getTotalAmount() + ")");
        }
        sale.setPaidAmount(paidAmount);
        sale.setDueAmount(sale.getTotalAmount().subtract(paidAmount));
        sale.setPaymentStatus(derivePaymentStatus(paidAmount, sale.getTotalAmount()));
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

    private void applyItems(Sale sale, List<SaleItemRequest> itemRequests, Set<Long> allowedInactiveProductIds) {
        Set<Long> seenProductIds = new HashSet<>();
        boolean isGst = sale.getGstType() == GstType.GST;
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (SaleItemRequest itemRequest : itemRequests) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("The same product cannot be added more than once in a sale");
            }

            Product product = productService.findProductOrThrow(itemRequest.getProductId());

            if (product.getStatus() == ProductStatus.INACTIVE
                    && !allowedInactiveProductIds.contains(product.getId())) {
                throw new BadRequestException("Product '" + product.getName() + "' is inactive and cannot be sold in new sales");
            }

            int availableStock = inventoryService.getCurrentStock(product.getId());
            if (itemRequest.getQuantity() > availableStock) {
                throw new BadRequestException("Insufficient stock for product '" + product.getName() + "': available "
                        + availableStock + ", requested " + itemRequest.getQuantity());
            }

            SalesOrderItem orderItem = null;
            if (itemRequest.getSalesOrderItemId() != null) {
                orderItem = salesOrderItemRepository.findById(itemRequest.getSalesOrderItemId())
                        .orElseThrow(() -> new BadRequestException("Sales order item not found: " + itemRequest.getSalesOrderItemId()));
                if (itemRequest.getQuantity() > orderItem.getRemainingQuantity()) {
                    throw new BadRequestException("Cannot bill " + itemRequest.getQuantity() + " of '" + product.getName()
                            + "': only " + orderItem.getRemainingQuantity() + " remaining on the order");
                }
            }

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal taxableAmount = quantity.multiply(itemRequest.getSellingPrice()).subtract(itemRequest.getDiscount());
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
                if (sale.getTaxMode() == TaxMode.INTER_STATE) {
                    igst = gstAmount;
                } else {
                    cgst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    sgst = gstAmount.subtract(cgst);
                }
            }

            BigDecimal subtotal = taxableAmount.add(gstAmount);

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .sellingPrice(itemRequest.getSellingPrice())
                    .discount(itemRequest.getDiscount())
                    .tax(gstAmount)
                    .subtotal(subtotal)
                    .gstPercent(gstPercent)
                    .taxableAmount(taxableAmount)
                    .cgstAmount(cgst)
                    .sgstAmount(sgst)
                    .igstAmount(igst)
                    .salesOrderItem(orderItem)
                    .build();

            sale.addItem(item);
            taxableTotal = taxableTotal.add(taxableAmount);
            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            igstTotal = igstTotal.add(igst);
            grandTotal = grandTotal.add(subtotal);
        }

        sale.setTaxableAmount(taxableTotal);
        sale.setCgstAmount(cgstTotal);
        sale.setSgstAmount(sgstTotal);
        sale.setIgstAmount(igstTotal);
        sale.setTotalAmount(grandTotal);
    }

    private void restoreStock(Sale sale, List<SaleItem> items) {
        String reason = "Sale cancelled: " + sale.getInvoiceNumber();
        for (SaleItem item : items) {
            inventoryService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    StockMovementType.SALE_CANCEL, ReferenceType.SALE, sale.getId(), reason);
        }
    }

    private void deductStock(Sale sale, List<SaleItem> items) {
        String reason = "Sale " + sale.getInvoiceNumber();
        for (SaleItem item : items) {
            inventoryService.applyMovement(item.getProduct().getId(), -item.getQuantity(),
                    StockMovementType.SALE, ReferenceType.SALE, sale.getId(), reason);
        }
    }

    public Sale findSaleOrThrow(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new SaleNotFoundException(id));
    }

    public long countTodaysSalesCount() {
        return saleRepository.count();
    }
}
