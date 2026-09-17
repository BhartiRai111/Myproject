package com.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/** Replace-all semantics: the given set becomes the user/employee's entire store assignment (Multi-Store spec section 70). */
@Getter
@Setter
public class AssignStoresRequest {

    @NotNull(message = "storeIds is required (use an empty array to clear all store access)")
    private Set<Long> storeIds;
}
