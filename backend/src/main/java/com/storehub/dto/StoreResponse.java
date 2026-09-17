package com.storehub.dto;

import com.storehub.entity.Store;
import com.storehub.entity.StoreStatus;
import com.storehub.entity.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class StoreResponse {
    private Long id;
    private String storeCode;
    private String storeName;
    private StoreType storeType;
    private String legalName;
    private String address;
    private Long countryId;
    private String countryName;
    private Long stateId;
    private String stateName;
    private String stateCode;
    private Long cityId;
    private String cityName;
    private Long zoneId;
    private String zoneName;
    private String pincode;
    private String phone;
    private String email;
    private String gstin;
    private StoreStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StoreResponse fromEntity(Store s) {
        return StoreResponse.builder()
                .id(s.getId())
                .storeCode(s.getStoreCode())
                .storeName(s.getStoreName())
                .storeType(s.getStoreType())
                .legalName(s.getLegalName())
                .address(s.getAddress())
                .countryId(s.getCountry() != null ? s.getCountry().getId() : null)
                .countryName(s.getCountry() != null ? s.getCountry().getName() : null)
                .stateId(s.getState() != null ? s.getState().getId() : null)
                .stateName(s.getState() != null ? s.getState().getName() : null)
                .stateCode(s.getState() != null ? s.getState().getCode() : null)
                .cityId(s.getCity() != null ? s.getCity().getId() : null)
                .cityName(s.getCity() != null ? s.getCity().getName() : null)
                .zoneId(s.getZone() != null ? s.getZone().getId() : null)
                .zoneName(s.getZone() != null ? s.getZone().getName() : null)
                .pincode(s.getPincode())
                .phone(s.getPhone())
                .email(s.getEmail())
                .gstin(s.getGstin())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
