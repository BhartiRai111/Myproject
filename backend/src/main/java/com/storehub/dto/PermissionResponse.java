package com.storehub.dto;

import com.storehub.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** A single permission, with its module, for the Roles &amp; Permissions admin page (spec section 62). */
@Getter
@AllArgsConstructor
public class PermissionResponse {

    private String name;
    private String module;

    public static PermissionResponse fromPermission(Permission permission) {
        return new PermissionResponse(permission.name(), permission.getModule());
    }
}
