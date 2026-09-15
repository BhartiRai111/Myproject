package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {
    private String query;
    private List<GlobalSearchResultItem> results;
}
