package com.storehub.service;

import com.storehub.dto.StoreComparisonResponse;
import com.storehub.dto.StoreComparisonRow;
import com.storehub.entity.Store;
import com.storehub.entity.User;
import com.storehub.repository.InventoryRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.StoreRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Branch/store comparison report (Multi-Store spec section 21): every metric here is
 * aggregated in the database per store, one query per store per metric — never
 * fetched in bulk and summed client-side. Scoped to the stores the caller can access
 * (spec section 14): an ALL_STORES user compares every store, a store-scoped user only
 * ever sees the stores they are assigned to, never a peer branch's figures.
 */
@Service
@RequiredArgsConstructor
public class StoreComparisonService {

    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public StoreComparisonResponse compare(LocalDate fromDate, LocalDate toDate) {
        User currentUser = SecurityUtil.currentUserOrNull();
        List<Long> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(currentUser);

        List<Store> stores = storeRepository.findAllById(accessibleStoreIds);
        stores.sort(Comparator.comparing(Store::getStoreName));

        List<StoreComparisonRow> rows = new ArrayList<>();
        for (Store store : stores) {
            rows.add(buildRow(store, fromDate, toDate));
        }

        return StoreComparisonResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .rows(rows)
                .build();
    }

    private StoreComparisonRow buildRow(Store store, LocalDate fromDate, LocalDate toDate) {
        Object[] salesAgg = saleRepository.sumAndCountByDateRangeAndStore(fromDate, toDate, store.getId()).get(0);
        Object[] purchaseAgg = purchaseRepository.sumAndCountByDateRangeAndStore(fromDate, toDate, store.getId()).get(0);

        return StoreComparisonRow.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .storeCode(store.getStoreCode())
                .totalSales((BigDecimal) salesAgg[0])
                .salesCount((long) salesAgg[1])
                .totalPurchases((BigDecimal) purchaseAgg[0])
                .purchaseCount((long) purchaseAgg[1])
                .totalStockUnits(inventoryRepository.sumCurrentStock(store.getId()))
                .lowStockCount(inventoryRepository.countLowStock(store.getId()))
                .outOfStockCount(inventoryRepository.countOutOfStock(store.getId()))
                .build();
    }
}
