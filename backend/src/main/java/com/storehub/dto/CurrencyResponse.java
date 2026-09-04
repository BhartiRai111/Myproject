package com.storehub.dto;

import com.storehub.entity.Currency;
import com.storehub.entity.CurrencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CurrencyResponse {
    private Long id;
    private String name;
    private String code;
    private String symbol;
    private Integer decimalPlaces;
    private CurrencyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CurrencyResponse fromEntity(Currency c) {
        return CurrencyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .symbol(c.getSymbol())
                .decimalPlaces(c.getDecimalPlaces())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
