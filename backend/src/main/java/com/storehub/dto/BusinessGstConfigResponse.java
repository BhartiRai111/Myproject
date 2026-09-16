package com.storehub.dto;

import com.storehub.entity.BusinessGstConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class BusinessGstConfigResponse {

    private Long id;
    private String legalName;
    private String tradeName;
    private String gstin;
    private String pan;
    private String address;
    private Long stateId;
    private String stateName;
    private String stateCode;
    private String pincode;
    /** True once legalName and gstin are both set — the frontend uses this to warn when Sales/Purchase GST calc has no seller anchor configured yet. */
    private boolean configured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BusinessGstConfigResponse fromEntity(BusinessGstConfig c) {
        return BusinessGstConfigResponse.builder()
                .id(c.getId())
                .legalName(c.getLegalName())
                .tradeName(c.getTradeName())
                .gstin(c.getGstin())
                .pan(c.getPan())
                .address(c.getAddress())
                .stateId(c.getState() != null ? c.getState().getId() : null)
                .stateName(c.getState() != null ? c.getState().getName() : null)
                .stateCode(c.getState() != null ? c.getState().getCode() : null)
                .pincode(c.getPincode())
                .configured(c.getLegalName() != null && !c.getLegalName().isBlank()
                        && c.getGstin() != null && !c.getGstin().isBlank())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
