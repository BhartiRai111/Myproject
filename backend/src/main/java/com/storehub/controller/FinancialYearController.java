package com.storehub.controller;

import com.storehub.dto.FinancialYearRequest;
import com.storehub.dto.FinancialYearResponse;
import com.storehub.dto.FinancialYearSummaryResponse;
import com.storehub.entity.FinancialYearStatus;
import com.storehub.service.FinancialYearService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Any authenticated user may VIEW financial years (the current FY must be visible everywhere); only ADMIN may mutate them. */
@RestController
@RequestMapping("/api/financial-years")
@RequiredArgsConstructor
public class FinancialYearController {

    private final FinancialYearService financialYearService;

    @GetMapping
    public ResponseEntity<List<FinancialYearResponse>> list() {
        return ResponseEntity.ok(financialYearService.list());
    }

    @GetMapping("/current")
    public ResponseEntity<FinancialYearResponse> current() {
        return ResponseEntity.ok(financialYearService.getCurrent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialYearResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(financialYearService.getById(id));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<FinancialYearSummaryResponse> summary(@PathVariable Long id) {
        return ResponseEntity.ok(financialYearService.summary(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_FY_MANAGE')")
    public ResponseEntity<FinancialYearResponse> create(@Valid @RequestBody FinancialYearRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financialYearService.create(request));
    }

    @PatchMapping("/{id}/open")
    @PreAuthorize("hasAuthority('PERM_FY_MANAGE')")
    public ResponseEntity<FinancialYearResponse> open(@PathVariable Long id) {
        return ResponseEntity.ok(financialYearService.setStatus(id, FinancialYearStatus.OPEN));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PERM_FY_MANAGE')")
    public ResponseEntity<FinancialYearResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(financialYearService.setStatus(id, FinancialYearStatus.CLOSED));
    }

    @PatchMapping("/{id}/mark-current")
    @PreAuthorize("hasAuthority('PERM_FY_MANAGE')")
    public ResponseEntity<FinancialYearResponse> markCurrent(@PathVariable Long id) {
        return ResponseEntity.ok(financialYearService.markCurrent(id));
    }
}
