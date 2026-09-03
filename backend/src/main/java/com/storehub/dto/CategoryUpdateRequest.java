package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryUpdateRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;
}
