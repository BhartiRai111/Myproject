package com.storehub.controller;

import com.storehub.dto.DayClosingCloseRequest;
import com.storehub.dto.DayClosingResponse;
import com.storehub.service.DayClosingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/day-closing")
@RequiredArgsConstructor
public class DayClosingController {

    private final DayClosingService dayClosingService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<DayClosingResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dayClosingService.computeSummary(date));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<List<DayClosingResponse>> history() {
        return ResponseEntity.ok(dayClosingService.history());
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<DayClosingResponse> close(@Valid @RequestBody DayClosingCloseRequest request) {
        return ResponseEntity.ok(dayClosingService.close(request));
    }
}
