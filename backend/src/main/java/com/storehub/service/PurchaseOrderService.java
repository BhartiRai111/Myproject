package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PurchaseOrderItemRequest;
import com.storehub.dto.PurchaseOrderRequest;
import com.storehub.dto.PurchaseOrderResponse;
import com.storehub.entity.Product;
import com.storehub.entity.PurchaseOrder;
import com.storehub.entity.PurchaseOrderItem;
import com.storehub.entity.PurchaseOrderStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.PurchaseOrderNotFoundException;
import com.storehub.repository.PurchaseOrderItemRepository;
import com.storehub.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final ProductService productService;
    private final SupplierService supplierService;

    public PagedResponse<PurchaseOrderResponse> search(String search, Long supplierId, PurchaseOrderStatus status,
                                                         LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseOrderResponse> result = purchaseOrderRepository
                .search(search, supplierId, status, fromDate, toDate, pageable)
                .map(PurchaseOrderResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public PurchaseOrderResponse getById(Long id) {
        return PurchaseOrderResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());
        if (supplier.getStatus() == SupplierStatus.INACTIVE) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchase orders");
        }

        PurchaseOrder order = PurchaseOrder.builder()
                .supplier(supplier)
                .supplierPhone(request.getSupplierPhone())
                .supplierGstin(request.getSupplierGstin())
                .billingAddress(request.getBillingAddress())
                .shippingAddress(request.getShippingAddress())
                .orderDate(request.getOrderDate())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .remarks(request.getRemarks())
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        applyItems(order, request.getItems());

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        saved.setOrderNumber(String.format("PO-%06d", saved.getId()));
        saved = purchaseOrderRepository.save(saved);

        return PurchaseOrderResponse.fromEntity(saved);
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = findOrThrow(id);

        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BadRequestException("A cancelled purchase order cannot be edited");
        }
        if (order.getStatus() == PurchaseOrderStatus.COMPLETED) {
            throw new BadRequestException("A fully received purchase order cannot be edited");
        }

        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());
        if (supplier.getStatus() == SupplierStatus.INACTIVE && !supplier.getId().equals(order.getSupplier().getId())) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchase orders");
        }

        order.setSupplier(supplier);
        order.setSupplierPhone(request.getSupplierPhone());
        order.setSupplierGstin(request.getSupplierGstin());
        order.setBillingAddress(request.getBillingAddress());
        order.setShippingAddress(request.getShippingAddress());
        order.setOrderDate(request.getOrderDate());
        order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        order.setRemarks(request.getRemarks());

        // Items already received (even partially) must stay present with at least their received quantity,
        // so a re-submitted item list can't silently drop receiving history.
        List<PurchaseOrderItem> previousItems = order.getItems();
        for (PurchaseOrderItem previous : previousItems) {
            if (previous.getReceivedQuantity() != null && previous.getReceivedQuantity() > 0) {
                boolean stillPresent = request.getItems().stream().anyMatch(i ->
                        i.getProductId().equals(previous.getProduct().getId())
                                && i.getQuantity() >= previous.getReceivedQuantity());
                if (!stillPresent) {
                    throw new BadRequestException("Product '" + previous.getProduct().getName()
                            + "' already has " + previous.getReceivedQuantity()
                            + " received and cannot be removed or reduced below that quantity");
                }
            }
        }

        order.clearItems();
        applyItems(order, request.getItems());
        recomputeStatus(order);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return PurchaseOrderResponse.fromEntity(saved);
    }

    @Transactional
    public PurchaseOrderResponse setStatus(Long id, PurchaseOrderStatus status) {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.CONFIRMED) {
            throw new BadRequestException("Status can only be manually set to Draft or Confirmed");
        }
        PurchaseOrder order = findOrThrow(id);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BadRequestException("A cancelled purchase order cannot change status");
        }
        boolean anyReceived = order.getItems().stream()
                .anyMatch(i -> i.getReceivedQuantity() != null && i.getReceivedQuantity() > 0);
        if (anyReceived) {
            throw new BadRequestException("This order already has received quantity and its status is now managed automatically");
        }
        order.setStatus(status);
        return PurchaseOrderResponse.fromEntity(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrder order = findOrThrow(id);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BadRequestException("This purchase order is already cancelled");
        }
        boolean anyReceived = order.getItems().stream()
                .anyMatch(i -> i.getReceivedQuantity() != null && i.getReceivedQuantity() > 0);
        if (anyReceived) {
            throw new BadRequestException("This order already has one or more purchase bills against it and cannot be cancelled");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return PurchaseOrderResponse.fromEntity(purchaseOrderRepository.save(order));
    }

    /** Applies a delta (positive when receiving, negative when reversing) to one order item's received quantity. */
    @Transactional
    public void applyReceivedQuantityDelta(Long purchaseOrderItemId, int delta) {
        PurchaseOrderItem item = purchaseOrderItemRepository.findById(purchaseOrderItemId)
                .orElseThrow(() -> new BadRequestException("Purchase order item not found: " + purchaseOrderItemId));
        int newReceived = (item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity()) + delta;
        if (newReceived < 0) {
            newReceived = 0;
        }
        if (newReceived > item.getQuantity()) {
            throw new BadRequestException("Cannot receive more than the ordered quantity for product '"
                    + item.getProduct().getName() + "'");
        }
        item.setReceivedQuantity(newReceived);
        purchaseOrderItemRepository.save(item);
        recomputeStatus(item.getPurchaseOrder());
        purchaseOrderRepository.save(item.getPurchaseOrder());
    }

    private void recomputeStatus(PurchaseOrder order) {
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return;
        }
        boolean allComplete = order.getItems().stream().allMatch(i -> i.getRemainingQuantity() <= 0);
        boolean anyReceived = order.getItems().stream()
                .anyMatch(i -> i.getReceivedQuantity() != null && i.getReceivedQuantity() > 0);

        if (!order.getItems().isEmpty() && allComplete) {
            order.setStatus(PurchaseOrderStatus.COMPLETED);
        } else if (anyReceived) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
    }

    private void applyItems(PurchaseOrder order, List<PurchaseOrderItemRequest> itemRequests) {
        Set<Long> seenProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseOrderItemRequest itemRequest : itemRequests) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("The same product cannot be added more than once in a purchase order");
            }

            Product product = productService.findProductOrThrow(itemRequest.getProductId());

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal taxableAmount = quantity.multiply(itemRequest.getRate()).subtract(itemRequest.getDiscount());
            if (taxableAmount.signum() < 0) {
                throw new BadRequestException("Discount cannot exceed the item amount for product '" + product.getName() + "'");
            }
            BigDecimal gstAmount = taxableAmount.multiply(itemRequest.getGstPercent())
                    .divide(BigDecimal.valueOf(100));
            BigDecimal totalAmount = taxableAmount.add(gstAmount);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .rate(itemRequest.getRate())
                    .discount(itemRequest.getDiscount())
                    .gstPercent(itemRequest.getGstPercent())
                    .taxableAmount(taxableAmount)
                    .gstAmount(gstAmount)
                    .totalAmount(totalAmount)
                    .receivedQuantity(0)
                    .build();

            order.addItem(item);
            total = total.add(totalAmount);
        }

        order.setTotalAmount(total);
    }

    public PurchaseOrder findOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    public long countPendingOrders() {
        return purchaseOrderRepository.countByStatusIn(
                List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.PARTIALLY_RECEIVED));
    }

    public long countTotalOrders() {
        return purchaseOrderRepository.count();
    }
}
