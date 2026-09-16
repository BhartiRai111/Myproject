package com.storehub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-time startup step: ensures every existing Product has an Inventory record,
 * seeding current stock from the legacy Product.stock_quantity column so
 * historical stock data is not lost when Inventory becomes the source of truth.
 * Also backfills a generated SKU onto any legacy Product that has none (SKU
 * Management spec sections 7/19) — safe to run on every startup since it is a
 * no-op once every product has a SKU.
 */
@Component
@RequiredArgsConstructor
public class InventoryStartupRunner implements ApplicationRunner {

    private final InventoryService inventoryService;
    private final ProductService productService;

    @Override
    public void run(ApplicationArguments args) {
        inventoryService.relaxLegacyStockQuantityColumn();
        inventoryService.backfillInventoryForExistingProducts();
        productService.backfillMissingSkus();
    }
}
