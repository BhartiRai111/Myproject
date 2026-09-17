package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The effective seller GST context for a store's own transactions (Multi-Store spec
 * section 32): when the store has its own GSTIN configured, that GSTIN/state is the
 * context; otherwise the business-wide {@link com.storehub.entity.BusinessGstConfig}
 * singleton is the fallback. {@code source} tells the caller which one was used, so a
 * frontend billing form can label the seller line accordingly.
 */
@Getter
@Builder
@AllArgsConstructor
public class StoreGstContextResponse {
    private Long storeId;
    private String gstin;
    private String legalName;
    private String stateCode;
    /** "STORE" when the store's own GSTIN override was used, "BUSINESS" when it fell back to the business-wide config. */
    private String source;
}
