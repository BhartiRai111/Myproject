package com.storehub.controller;

import com.storehub.dto.GlobalSearchResponse;
import com.storehub.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping("/api/search")
    public GlobalSearchResponse search(@RequestParam(defaultValue = "") String q) {
        return globalSearchService.search(q);
    }
}
