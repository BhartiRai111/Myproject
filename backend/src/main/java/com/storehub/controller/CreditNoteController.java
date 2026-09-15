package com.storehub.controller;

import com.storehub.dto.CreditNoteCreateRequest;
import com.storehub.dto.CreditNoteResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.NoteStatus;
import com.storehub.service.CreditNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @GetMapping
    public ResponseEntity<PagedResponse<CreditNoteResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(creditNoteService.search(search, status, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditNoteResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(creditNoteService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CreditNoteResponse> create(@Valid @RequestBody CreditNoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditNoteService.create(request));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CreditNoteResponse> post(@PathVariable Long id) {
        return ResponseEntity.ok(creditNoteService.post(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<CreditNoteResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(creditNoteService.cancel(id));
    }
}
