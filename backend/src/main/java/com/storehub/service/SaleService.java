package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.SaleCreateRequest;
import com.storehub.dto.SaleItemRequest;
import com.storehub.dto.SaleResponse;
import com.storehub.dto.SaleUpdateRequest;
import com.storehub.entity.Customer;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.Sale;
import com.storehub.entity.SaleItem;
import com.storehub.entity.SaleStatus;
import com.storehub.entity.StockMovementType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.SaleNotFoundException;
import com.storehub.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ProductService productService;
    private final CustomerService customerService;
    private final InventoryService inventoryService;

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
        return SaleResponse.fromEntity(findSaleOrThrow(id));
    }

    @Transactional
    public SaleResponse createSale(SaleCreateRequest request) {
        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        Sale sale = Sale.builder()
                .customer(customer)
                .saleDate(request.getSaleDate())
                .paymentStatus(request.getPaymentStatus())
                .status(SaleStatus.COMPLETED)
                .notes(request.getNotes())
                .build();

        applyItems(sale, request.getItems(), Collections.emptySet());

        Sale saved = saleRepository.save(sale);
        saved.setInvoiceNumber(String.format("INV-%06d", saved.getId()));
        saved = saleRepository.save(saved);
        deductStock(saved, saved.getItems());

        return SaleResponse.fromEntity(saved);
    }

    @Transactional
    public SaleResponse updateSale(Long id, SaleUpdateRequest request) {
        Sale sale = findSaleOrThrow(id);

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("A cancelled sale cannot be edited");
        }

        if (request.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("Use the cancel action to cancel a sale");
        }

        List<SaleItem> oldItems = new ArrayList<>(sale.getItems());
        Set<Long> allowedInactiveProductIds = oldItems.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        boolean oldWasCompleted = sale.getStatus() == SaleStatus.COMPLETED;
        if (oldWasCompleted) {
            restoreStock(sale, oldItems);
        }

        Customer customer = request.getCustomerId() != null
                ? customerService.findCustomerOrThrow(request.getCustomerId())
                : null;

        sale.setCustomer(customer);
        sale.setSaleDate(request.getSaleDate());
        sale.setPaymentStatus(request.getPaymentStatus());
        sale.setNotes(request.getNotes());

        sale.clearItems();
        applyItems(sale, request.getItems(), allowedInactiveProductIds);

        if (request.getStatus() == SaleStatus.COMPLETED) {
            deductStock(sale, sale.getItems());
        }
        sale.setStatus(request.getStatus());

        Sale saved = saleRepository.save(sale);
        return SaleResponse.fromEntity(saved);
    }

    @Transactional
    public SaleResponse cancelSale(Long id) {
        Sale sale = findSaleOrThrow(id);

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("This sale is already cancelled");
        }

        if (sale.getStatus() == SaleStatus.COMPLETED) {
            restoreStock(sale, sale.getItems());
        }

        sale.setStatus(SaleStatus.CANCELLED);
        return SaleResponse.fromEntity(saleRepository.save(sale));
    }

    private void applyItems(Sale sale, List<SaleItemRequest> itemRequests, Set<Long> allowedInactiveProductIds) {
        Set<Long> seenProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

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

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal subtotal = quantity.multiply(itemRequest.getSellingPrice())
                    .subtract(itemRequest.getDiscount())
                    .add(itemRequest.getTax());

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .sellingPrice(itemRequest.getSellingPrice())
                    .discount(itemRequest.getDiscount())
                    .tax(itemRequest.getTax())
                    .subtotal(subtotal)
                    .build();

            sale.addItem(item);
            total = total.add(subtotal);
        }

        sale.setTotalAmount(total);
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

    private Sale findSaleOrThrow(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new SaleNotFoundException(id));
    }
}
