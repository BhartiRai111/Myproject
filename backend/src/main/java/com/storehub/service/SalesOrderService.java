package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.SalesOrderItemRequest;
import com.storehub.dto.SalesOrderRequest;
import com.storehub.dto.SalesOrderResponse;
import com.storehub.entity.Customer;
import com.storehub.entity.Product;
import com.storehub.entity.SalesOrder;
import com.storehub.entity.SalesOrderItem;
import com.storehub.entity.SalesOrderStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.SalesOrderNotFoundException;
import com.storehub.repository.SalesOrderItemRepository;
import com.storehub.repository.SalesOrderRepository;
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
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final ProductService productService;
    private final CustomerService customerService;

    public PagedResponse<SalesOrderResponse> search(String search, Long customerId, SalesOrderStatus status,
                                                      LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SalesOrderResponse> result = salesOrderRepository
                .search(search, customerId, status, fromDate, toDate, pageable)
                .map(SalesOrderResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public SalesOrderResponse getById(Long id) {
        return SalesOrderResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        SalesOrder order = SalesOrder.builder()
                .customer(customer)
                .customerPhone(request.getCustomerPhone())
                .customerGstin(request.getCustomerGstin())
                .billingAddress(request.getBillingAddress())
                .shippingAddress(request.getShippingAddress())
                .orderDate(request.getOrderDate())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .remarks(request.getRemarks())
                .status(SalesOrderStatus.DRAFT)
                .build();

        applyItems(order, request.getItems());

        SalesOrder saved = salesOrderRepository.save(order);
        saved.setOrderNumber(String.format("SO-%06d", saved.getId()));
        saved = salesOrderRepository.save(saved);

        return SalesOrderResponse.fromEntity(saved);
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        SalesOrder order = findOrThrow(id);

        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new BadRequestException("A cancelled sales order cannot be edited");
        }
        if (order.getStatus() == SalesOrderStatus.COMPLETED) {
            throw new BadRequestException("A fully billed sales order cannot be edited");
        }

        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        order.setCustomer(customer);
        order.setCustomerPhone(request.getCustomerPhone());
        order.setCustomerGstin(request.getCustomerGstin());
        order.setBillingAddress(request.getBillingAddress());
        order.setShippingAddress(request.getShippingAddress());
        order.setOrderDate(request.getOrderDate());
        order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        order.setRemarks(request.getRemarks());

        // Items already billed (even partially) must stay present with at least their billed quantity,
        // so a re-submitted item list can't silently drop billing history.
        List<SalesOrderItem> previousItems = order.getItems();
        for (SalesOrderItem previous : previousItems) {
            if (previous.getBilledQuantity() != null && previous.getBilledQuantity() > 0) {
                boolean stillPresent = request.getItems().stream().anyMatch(i ->
                        i.getProductId().equals(previous.getProduct().getId())
                                && i.getQuantity() >= previous.getBilledQuantity());
                if (!stillPresent) {
                    throw new BadRequestException("Product '" + previous.getProduct().getName()
                            + "' already has " + previous.getBilledQuantity()
                            + " billed and cannot be removed or reduced below that quantity");
                }
            }
        }

        order.clearItems();
        applyItems(order, request.getItems());
        recomputeStatus(order);

        SalesOrder saved = salesOrderRepository.save(order);
        return SalesOrderResponse.fromEntity(saved);
    }

    @Transactional
    public SalesOrderResponse setStatus(Long id, SalesOrderStatus status) {
        if (status != SalesOrderStatus.DRAFT && status != SalesOrderStatus.CONFIRMED) {
            throw new BadRequestException("Status can only be manually set to Draft or Confirmed");
        }
        SalesOrder order = findOrThrow(id);
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new BadRequestException("A cancelled sales order cannot change status");
        }
        boolean anyBilled = order.getItems().stream()
                .anyMatch(i -> i.getBilledQuantity() != null && i.getBilledQuantity() > 0);
        if (anyBilled) {
            throw new BadRequestException("This order already has billed quantity and its status is now managed automatically");
        }
        order.setStatus(status);
        return SalesOrderResponse.fromEntity(salesOrderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrder order = findOrThrow(id);
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new BadRequestException("This sales order is already cancelled");
        }
        boolean anyBilled = order.getItems().stream()
                .anyMatch(i -> i.getBilledQuantity() != null && i.getBilledQuantity() > 0);
        if (anyBilled) {
            throw new BadRequestException("This order already has one or more sales bills against it and cannot be cancelled");
        }
        order.setStatus(SalesOrderStatus.CANCELLED);
        return SalesOrderResponse.fromEntity(salesOrderRepository.save(order));
    }

    /** Applies a delta (positive when billing, negative when reversing) to one order item's billed quantity. */
    @Transactional
    public void applyBilledQuantityDelta(Long salesOrderItemId, int delta) {
        SalesOrderItem item = salesOrderItemRepository.findById(salesOrderItemId)
                .orElseThrow(() -> new BadRequestException("Sales order item not found: " + salesOrderItemId));
        int newBilled = (item.getBilledQuantity() == null ? 0 : item.getBilledQuantity()) + delta;
        if (newBilled < 0) {
            newBilled = 0;
        }
        if (newBilled > item.getQuantity()) {
            throw new BadRequestException("Cannot bill more than the ordered quantity for product '"
                    + item.getProduct().getName() + "'");
        }
        item.setBilledQuantity(newBilled);
        salesOrderItemRepository.save(item);
        recomputeStatus(item.getSalesOrder());
        salesOrderRepository.save(item.getSalesOrder());
    }

    private void recomputeStatus(SalesOrder order) {
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            return;
        }
        boolean allComplete = order.getItems().stream().allMatch(i -> i.getRemainingQuantity() <= 0);
        boolean anyBilled = order.getItems().stream()
                .anyMatch(i -> i.getBilledQuantity() != null && i.getBilledQuantity() > 0);

        if (!order.getItems().isEmpty() && allComplete) {
            order.setStatus(SalesOrderStatus.COMPLETED);
        } else if (anyBilled) {
            order.setStatus(SalesOrderStatus.PARTIALLY_BILLED);
        }
    }

    private void applyItems(SalesOrder order, List<SalesOrderItemRequest> itemRequests) {
        Set<Long> seenProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (SalesOrderItemRequest itemRequest : itemRequests) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("The same product cannot be added more than once in a sales order");
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

            SalesOrderItem item = SalesOrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .rate(itemRequest.getRate())
                    .discount(itemRequest.getDiscount())
                    .gstPercent(itemRequest.getGstPercent())
                    .taxableAmount(taxableAmount)
                    .gstAmount(gstAmount)
                    .totalAmount(totalAmount)
                    .billedQuantity(0)
                    .build();

            order.addItem(item);
            total = total.add(totalAmount);
        }

        order.setTotalAmount(total);
    }

    public SalesOrder findOrThrow(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));
    }

    public long countPendingOrders() {
        return salesOrderRepository.countByStatusIn(
                List.of(SalesOrderStatus.DRAFT, SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_BILLED));
    }

    public long countTotalOrders() {
        return salesOrderRepository.count();
    }
}
