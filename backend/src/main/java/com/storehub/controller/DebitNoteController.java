package com.storehub.controller;

import com.storehub.dto.DebitNoteCreateRequest;
import com.storehub.dto.DebitNoteResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.NoteStatus;
import com.storehub.service.DebitNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/debit-notes")
@RequiredArgsConstructor
public class DebitNoteController {

    private final DebitNoteService debitNoteService;

    @GetMapping
    public ResponseEntity<PagedResponse<DebitNoteResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(debitNoteService.search(search, status, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DebitNoteResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(debitNoteService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_DEBIT_NOTE_CREATE')")
    public ResponseEntity<DebitNoteResponse> create(@Valid @RequestBody DebitNoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debitNoteService.create(request));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PERM_DEBIT_NOTE_POST')")
    public ResponseEntity<DebitNoteResponse> post(@PathVariable Long id) {
        return ResponseEntity.ok(debitNoteService.post(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERM_DEBIT_NOTE_CANCEL')")
    public ResponseEntity<DebitNoteResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(debitNoteService.cancel(id));
    }
}
