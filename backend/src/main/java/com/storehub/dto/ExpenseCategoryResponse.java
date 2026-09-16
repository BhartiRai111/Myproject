package com.storehub.dto;

import com.storehub.entity.ExpenseCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseCategoryResponse {
    private Long id;
    private String code;
    private String name;
    private Long linkedAccountId;
    private String linkedAccountName;
    private boolean active;
    /** "ACTIVE"/"INACTIVE" mirror of {@link #active} — lets the frontend reuse its generic MasterCrudPage status filter. */
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseCategoryResponse fromEntity(ExpenseCategory c) {
        return ExpenseCategoryResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .linkedAccountId(c.getLinkedAccount() != null ? c.getLinkedAccount().getId() : null)
                .linkedAccountName(c.getLinkedAccount() != null ? c.getLinkedAccount().getAccountName() : null)
                .active(c.isActive())
                .status(c.isActive() ? "ACTIVE" : "INACTIVE")
                .description(c.getDescription())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
