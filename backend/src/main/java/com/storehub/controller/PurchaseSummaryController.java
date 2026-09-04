package com.storehub.controller;

import com.storehub.dto.PurchaseSummaryResponse;
import com.storehub.service.PurchaseSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-summary")
@RequiredArgsConstructor
public class PurchaseSummaryController {

    private final PurchaseSummaryService purchaseSummaryService;

    @GetMapping
    public ResponseEntity<PurchaseSummaryResponse> getSummary() {
        return ResponseEntity.ok(purchaseSummaryService.getSummary());
    }
}
