package com.storehub.controller;

import com.storehub.dto.StoreComparisonResponse;
import com.storehub.service.StoreComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Branch/store comparison report (Multi-Store spec section 21) — every authenticated user may call this; the rows returned are already scoped to their own accessible stores. */
@RestController
@RequestMapping("/api/reports/store-comparison")
@RequiredArgsConstructor
public class StoreComparisonController {

    private final StoreComparisonService storeComparisonService;

    @GetMapping
    public ResponseEntity<StoreComparisonResponse> compare(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(storeComparisonService.compare(fromDate, toDate));
    }
}
