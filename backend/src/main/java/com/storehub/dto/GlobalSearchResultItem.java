package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row in the Global Search dropdown (spec section 7) — enough to render and to navigate straight to the source document. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResultItem {
    private String category;
    private Long id;
    private String title;
    private String subtitle;
    private String path;
}
