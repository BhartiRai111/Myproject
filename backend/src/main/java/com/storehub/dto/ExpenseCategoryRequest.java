package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseCategoryRequest {

    @NotBlank(message = "Category code is required")
    private String code;

    @NotBlank(message = "Category name is required")
    private String name;

    /** Optional Chart-of-Accounts account this category posts expenses to; null falls back to the generic Expenses account. */
    private Long linkedAccountId;

    private String description;
}
