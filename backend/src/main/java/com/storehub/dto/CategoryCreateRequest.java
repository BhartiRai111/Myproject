package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryCreateRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private String itemType;

    private String applicableProperty;
}
