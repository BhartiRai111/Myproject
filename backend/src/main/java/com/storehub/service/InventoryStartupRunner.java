package com.storehub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-time startup step: ensures every existing Product has an Inventory record,
 * seeding current stock from the legacy Product.stock_quantity column so
 * historical stock data is not lost when Inventory becomes the source of truth.
 */
@Component
@RequiredArgsConstructor
public class InventoryStartupRunner implements ApplicationRunner {

    private final InventoryService inventoryService;

    @Override
    public void run(ApplicationArguments args) {
        inventoryService.backfillInventoryForExistingProducts();
    }
}
