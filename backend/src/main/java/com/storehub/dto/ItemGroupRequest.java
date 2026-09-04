package com.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemGroupRequest {

    @NotBlank(message = "Item group name is required")
    private String name;

    private String description;
}
