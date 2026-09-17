package com.storehub.controller;

import com.storehub.dto.GstLiabilityResponse;
import com.storehub.dto.GstReportListResponse;
import com.storehub.dto.Gstr1Response;
import com.storehub.dto.Gstr3bResponse;
import com.storehub.dto.HsnSummaryReportResponse;
import com.storehub.dto.PurchaseGstReportResponse;
import com.storehub.dto.ReconciliationResponse;
import com.storehub.dto.TaxRateSummaryReportResponse;
import com.storehub.service.GstReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/gst-reports")
@RequiredArgsConstructor
public class GstReportController {

    private final GstReportingService gstReportingService;

    @GetMapping("/gstr1")
    public ResponseEntity<Gstr1Response> gstr1(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String returnPeriod,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.gstr1(fromDate, toDate, returnPeriod, storeId));
    }

    @GetMapping("/purchase")
    public ResponseEntity<PurchaseGstReportResponse> purchaseGstReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String returnPeriod,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voucherDate"));
        return ResponseEntity.ok(gstReportingService.purchaseGstReport(fromDate, toDate, returnPeriod, storeId, pageable));
    }

    @GetMapping("/gstr3b")
    public ResponseEntity<Gstr3bResponse> gstr3b(@RequestParam String returnPeriod, @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.gstr3bSummary(returnPeriod, storeId));
    }

    @GetMapping("/output")
    public ResponseEntity<GstReportListResponse> outputGst(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voucherDate"));
        return ResponseEntity.ok(gstReportingService.outputGstReport(fromDate, toDate, storeId, pageable));
    }

    @GetMapping("/input")
    public ResponseEntity<GstReportListResponse> inputGst(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voucherDate"));
        return ResponseEntity.ok(gstReportingService.inputGstReport(fromDate, toDate, storeId, pageable));
    }

    @GetMapping("/hsn")
    public ResponseEntity<HsnSummaryReportResponse> hsnSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.hsnSummary(fromDate, toDate, storeId));
    }

    @GetMapping("/tax-rate")
    public ResponseEntity<TaxRateSummaryReportResponse> taxRateSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.taxRateSummary(fromDate, toDate, storeId));
    }

    @GetMapping("/liability")
    public ResponseEntity<GstLiabilityResponse> liability(@RequestParam String returnPeriod, @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.gstLiability(returnPeriod, storeId));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ReconciliationResponse> reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(gstReportingService.reconciliation(fromDate, toDate, storeId));
    }
}
