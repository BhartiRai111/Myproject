package com.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetCurrentStoreRequest {

    @NotNull(message = "storeId is required")
    private Long storeId;
}
