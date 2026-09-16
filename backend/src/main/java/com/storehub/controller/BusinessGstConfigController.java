package com.storehub.controller;

import com.storehub.dto.BusinessGstConfigRequest;
import com.storehub.dto.BusinessGstConfigResponse;
import com.storehub.service.BusinessGstConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Any authenticated user may VIEW the business's GST configuration (Sales/Purchase forms need it for tax-mode suggestion); only ADMIN may edit it. */
@RestController
@RequestMapping("/api/masters/business-gst-config")
@RequiredArgsConstructor
public class BusinessGstConfigController {

    private final BusinessGstConfigService businessGstConfigService;

    @GetMapping
    public ResponseEntity<BusinessGstConfigResponse> get() {
        return ResponseEntity.ok(businessGstConfigService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_GST_CONFIG')")
    public ResponseEntity<BusinessGstConfigResponse> update(@Valid @RequestBody BusinessGstConfigRequest request) {
        return ResponseEntity.ok(businessGstConfigService.update(request));
    }
}
