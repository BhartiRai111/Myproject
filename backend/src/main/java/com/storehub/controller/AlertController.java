package com.storehub.controller;

import com.storehub.dto.AlertItem;
import com.storehub.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/api/alerts")
    public ResponseEntity<List<AlertItem>> getAlerts() {
        return ResponseEntity.ok(alertService.getAlerts());
    }
}
