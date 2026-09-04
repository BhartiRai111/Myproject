package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseItemRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.PurchaseUpdateRequest;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseItem;
import com.storehub.entity.PurchaseStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.PurchaseNotFoundException;
import com.storehub.repository.PurchaseRepository;
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
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ProductService productService;
    private final SupplierService supplierService;

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
        return PurchaseResponse.fromEntity(findPurchaseOrThrow(id));
    }

    @Transactional
    public PurchaseResponse createPurchase(PurchaseCreateRequest request) {
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());

        if (supplier.getStatus() == SupplierStatus.INACTIVE) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchases");
        }

        Purchase purchase = Purchase.builder()
                .supplier(supplier)
                .purchaseDate(request.getPurchaseDate())
                .paymentStatus(request.getPaymentStatus())
                .status(PurchaseStatus.PENDING)
                .notes(request.getNotes())
                .build();

        applyItems(purchase, request.getItems(), Collections.emptySet());

        Purchase saved = purchaseRepository.save(purchase);
        saved.setPurchaseNumber(String.format("PUR-%06d", saved.getId()));
        saved = purchaseRepository.save(saved);

        return PurchaseResponse.fromEntity(saved);
    }

    @Transactional
    public PurchaseResponse updatePurchase(Long id, PurchaseUpdateRequest request) {
        Purchase purchase = findPurchaseOrThrow(id);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("A cancelled purchase cannot be edited");
        }

        if (request.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("Use the cancel action to cancel a purchase");
        }

        List<PurchaseItem> oldItems = new ArrayList<>(purchase.getItems());
        Set<Long> allowedInactiveProductIds = oldItems.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        boolean oldWasCompleted = purchase.getStatus() == PurchaseStatus.COMPLETED;
        if (oldWasCompleted) {
            restoreStock(oldItems);
        }

        Long oldSupplierId = purchase.getSupplier().getId();
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());

        if (supplier.getStatus() == SupplierStatus.INACTIVE && !supplier.getId().equals(oldSupplierId)) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new purchases");
        }

        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.getPurchaseDate());
        purchase.setPaymentStatus(request.getPaymentStatus());
        purchase.setNotes(request.getNotes());

        purchase.clearItems();
        applyItems(purchase, request.getItems(), allowedInactiveProductIds);

        if (request.getStatus() == PurchaseStatus.COMPLETED) {
            addStock(purchase.getItems());
        }
        purchase.setStatus(request.getStatus());

        Purchase saved = purchaseRepository.save(purchase);
        return PurchaseResponse.fromEntity(saved);
    }

    @Transactional
    public PurchaseResponse cancelPurchase(Long id) {
        Purchase purchase = findPurchaseOrThrow(id);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("This purchase is already cancelled");
        }

        if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
            restoreStock(purchase.getItems());
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);
        return PurchaseResponse.fromEntity(purchaseRepository.save(purchase));
    }

    @Transactional
    public void deletePurchase(Long id) {
        Purchase purchase = findPurchaseOrThrow(id);

        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BadRequestException("Only a pending purchase can be deleted; cancel it instead to keep purchase history");
        }

        purchaseRepository.delete(purchase);
    }

    private void applyItems(Purchase purchase, List<PurchaseItemRequest> itemRequests,
                             Set<Long> allowedInactiveProductIds) {
        Set<Long> seenProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseItemRequest itemRequest : itemRequests) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("The same product cannot be added more than once in a purchase");
            }

            Product product = productService.findProductOrThrow(itemRequest.getProductId());

            if (product.getStatus() == ProductStatus.INACTIVE
                    && !allowedInactiveProductIds.contains(product.getId())) {
                throw new BadRequestException("Product '" + product.getName() + "' is inactive and cannot be used in new purchases");
            }

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal subtotal = quantity.multiply(itemRequest.getPurchasePrice())
                    .subtract(itemRequest.getDiscount())
                    .add(itemRequest.getTax());

            PurchaseItem item = PurchaseItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .purchasePrice(itemRequest.getPurchasePrice())
                    .discount(itemRequest.getDiscount())
                    .tax(itemRequest.getTax())
                    .subtotal(subtotal)
                    .build();

            purchase.addItem(item);
            total = total.add(subtotal);
        }

        purchase.setTotalAmount(total);
    }

    private void addStock(List<PurchaseItem> items) {
        for (PurchaseItem item : items) {
            productService.adjustStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    private void restoreStock(List<PurchaseItem> items) {
        for (PurchaseItem item : items) {
            productService.adjustStock(item.getProduct().getId(), -item.getQuantity());
        }
    }

    private Purchase findPurchaseOrThrow(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
    }
}
