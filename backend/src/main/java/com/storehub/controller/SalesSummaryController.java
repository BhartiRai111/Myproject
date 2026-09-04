package com.storehub.controller;

import com.storehub.dto.SalesSummaryResponse;
import com.storehub.service.SalesSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-summary")
@RequiredArgsConstructor
public class SalesSummaryController {

    private final SalesSummaryService salesSummaryService;

    @GetMapping
    public ResponseEntity<SalesSummaryResponse> getSummary() {
        return ResponseEntity.ok(salesSummaryService.getSummary());
    }
}
