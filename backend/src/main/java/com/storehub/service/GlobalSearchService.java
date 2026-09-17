package com.storehub.service;

import com.storehub.dto.GlobalSearchResponse;
import com.storehub.dto.GlobalSearchResultItem;
import com.storehub.entity.CreditNote;
import com.storehub.entity.DebitNote;
import com.storehub.entity.Payment;
import com.storehub.entity.Product;
import com.storehub.entity.Purchase;
import com.storehub.entity.PurchaseOrder;
import com.storehub.entity.Receipt;
import com.storehub.entity.Sale;
import com.storehub.entity.SalesOrder;
import com.storehub.entity.Supplier;
import com.storehub.entity.TransactionType;
import com.storehub.repository.CreditNoteRepository;
import com.storehub.repository.DebitNoteRepository;
import com.storehub.repository.PaymentRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.PurchaseOrderRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.ReceiptRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.SalesOrderRepository;
import com.storehub.repository.SupplierRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Global Search across every document type (spec section 7): searches each module's
 * existing paginated `search(...)` query (the same one that backs its list page) with a
 * small page size, so results and filtering logic never drift from the module itself.
 * Each result carries a ready-to-navigate frontend path. Store-sensitive categories
 * (Sale, Sales Order) are narrowed to the caller's own accessible store(s) — an ADMIN
 * (ALL_STORES) searches every store, a store-scoped user only their own (Multi-Store
 * spec section 76's explicit cross-store search authorization requirement).
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private static final int LIMIT_PER_CATEGORY = 5;

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final DebitNoteRepository debitNoteRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final StoreAccessService storeAccessService;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String query) {
        String q = query == null ? "" : query.trim();
        List<GlobalSearchResultItem> results = new ArrayList<>();
        if (q.length() < 2) {
            return GlobalSearchResponse.builder().query(q).results(results).build();
        }

        Pageable top = PageRequest.of(0, LIMIT_PER_CATEGORY);
        // Global Search never forces a store pick the way a list page does (spec section
        // 14's "please select a store" only applies to a dedicated list view) — a
        // store-scoped user with no current store simply gets no store-sensitive results
        // rather than an error breaking the whole search.
        Long storeId = resolveSearchStoreIdOrNull();

        for (Sale sale : saleRepository.search(q, null, null, null, null, null, storeId, top).getContent()) {
            boolean challan = sale.getTransactionType() == TransactionType.SALE_CHALLAN;
            results.add(GlobalSearchResultItem.builder()
                    .category("Sale").id(sale.getId()).title(sale.getInvoiceNumber())
                    .subtitle(sale.getCustomer() != null
                            ? sale.getCustomer().getFirstName() + " " + (sale.getCustomer().getLastName() != null ? sale.getCustomer().getLastName() : "")
                            : "Walk-in")
                    .path(challan ? "/sales/kacchi/" + sale.getId() : "/sales/bills/" + sale.getId())
                    .build());
        }

        for (Purchase purchase : purchaseRepository.search(q, null, null, null, null, null, storeId, top).getContent()) {
            boolean challan = purchase.getTransactionType() == TransactionType.PURCHASE_CHALLAN;
            results.add(GlobalSearchResultItem.builder()
                    .category("Purchase").id(purchase.getId()).title(purchase.getPurchaseNumber())
                    .subtitle(purchase.getSupplier() != null ? purchase.getSupplier().getName() : "")
                    .path(challan ? "/purchases/kacchi/" + purchase.getId() : "/purchases/bills/" + purchase.getId())
                    .build());
        }

        for (Product product : productRepository.search(q, null, null, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Product").id(product.getId()).title(product.getName())
                    .subtitle(product.getSku() != null ? "SKU: " + product.getSku() : "")
                    .path("/products/" + product.getId() + "/edit")
                    .build());
        }

        for (Supplier supplier : supplierRepository.search(q, null, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Supplier").id(supplier.getId()).title(supplier.getName())
                    .subtitle(supplier.getMobile())
                    .path("/suppliers/" + supplier.getId() + "/edit")
                    .build());
        }

        for (CreditNote note : creditNoteRepository.search(q, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Credit Note").id(note.getId()).title(note.getVoucherNumber())
                    .subtitle(note.getCustomer() != null
                            ? note.getCustomer().getFirstName() + " " + (note.getCustomer().getLastName() != null ? note.getCustomer().getLastName() : "")
                            : "")
                    .path("/sales/credit-notes/" + note.getId())
                    .build());
        }

        for (DebitNote note : debitNoteRepository.search(q, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Debit Note").id(note.getId()).title(note.getVoucherNumber())
                    .subtitle(note.getSupplier() != null ? note.getSupplier().getName() : "")
                    .path("/purchases/debit-notes/" + note.getId())
                    .build());
        }

        for (SalesOrder order : salesOrderRepository.search(q, null, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Sales Order").id(order.getId()).title(order.getOrderNumber())
                    .subtitle(order.getCustomer() != null
                            ? order.getCustomer().getFirstName() + " " + (order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "")
                            : "")
                    .path("/sales/orders/" + order.getId())
                    .build());
        }

        for (PurchaseOrder order : purchaseOrderRepository.search(q, null, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Purchase Order").id(order.getId()).title(order.getOrderNumber())
                    .subtitle(order.getSupplier() != null ? order.getSupplier().getName() : "")
                    .path("/purchases/orders/" + order.getId())
                    .build());
        }

        for (Receipt receipt : receiptRepository.search(q, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Receipt").id(receipt.getId()).title(receipt.getReceiptNumber())
                    .subtitle(receipt.getCustomer() != null
                            ? receipt.getCustomer().getFirstName() + " " + (receipt.getCustomer().getLastName() != null ? receipt.getCustomer().getLastName() : "")
                            : "")
                    .path("/sales/receipts/" + receipt.getId())
                    .build());
        }

        for (Payment payment : paymentRepository.search(q, null, null, null, storeId, top).getContent()) {
            results.add(GlobalSearchResultItem.builder()
                    .category("Payment").id(payment.getId()).title(payment.getPaymentNumber())
                    .subtitle(payment.getSupplier() != null ? payment.getSupplier().getName() : "")
                    .path("/purchases/payments/" + payment.getId())
                    .build());
        }

        return GlobalSearchResponse.builder().query(q).results(results).build();
    }

    /**
     * null = no filter (ADMIN/ALL_STORES sees every store). A store-scoped caller is
     * narrowed to their current store, or their one accessible store when they have
     * exactly one. With several accessible stores and none currently selected, the
     * single-storeId filter this query supports can't safely express "any of mine"
     * without either leaking every store (null) or erroring the whole search — so it
     * returns a sentinel id no real store can ever have, which quietly yields zero
     * store-sensitive results instead.
     */
    private Long resolveSearchStoreIdOrNull() {
        var user = SecurityUtil.currentUserOrNull();
        if (user == null || storeAccessService.hasAllStoresAccess(user)) {
            return null;
        }
        if (user.getCurrentStore() != null) {
            return user.getCurrentStore().getId();
        }
        List<Long> accessible = storeAccessService.getAccessibleStoreIds(user);
        return accessible.size() == 1 ? accessible.get(0) : NO_ACCESSIBLE_STORE_SENTINEL;
    }

    private static final Long NO_ACCESSIBLE_STORE_SENTINEL = -1L;
}
