package com.storehub.dto;

import com.storehub.entity.Role;
import com.storehub.service.RoleDescriptions;
import com.storehub.service.RolePermissions;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Role list row (spec section 60): name, description, active, permission count — never the full permission set here. */
@Getter
@AllArgsConstructor
public class RoleResponse {

    private String name;
    private String description;
    private boolean active;
    private int permissionCount;

    public static RoleResponse fromRole(Role role) {
        return new RoleResponse(role.name(), RoleDescriptions.of(role), true, RolePermissions.forRole(role).size());
    }
}
