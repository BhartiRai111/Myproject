package com.storehub.dto;

import com.storehub.entity.AccountGroup;
import com.storehub.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountGroupResponse {
    private Long id;
    private String name;
    private AccountType accountType;
    private Long parentGroupId;
    private String parentGroupName;
    private boolean active;

    public static AccountGroupResponse fromEntity(AccountGroup group) {
        return AccountGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .accountType(group.getAccountType())
                .parentGroupId(group.getParentGroup() != null ? group.getParentGroup().getId() : null)
                .parentGroupName(group.getParentGroup() != null ? group.getParentGroup().getName() : null)
                .active(group.isActive())
                .build();
    }
}
