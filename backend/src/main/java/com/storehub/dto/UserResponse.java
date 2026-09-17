package com.storehub.dto;

import com.storehub.entity.Permission;
import com.storehub.entity.Role;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.service.RolePermissions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String mobile;
    private Role role;
    private UserStatus status;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private boolean mustChangePassword;
    private LocalDateTime lastLogin;
    /** Multi-Store spec section 11 — true if this user's role carries STORE_ACCESS_ALL (ADMIN by default), bypassing store scoping entirely. */
    private boolean allStoresAccess;
    private Long currentStoreId;
    private String currentStoreName;
    private String currentStoreCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .role(user.getRole())
                .status(user.getStatus())
                .employeeId(user.getEmployee() != null ? user.getEmployee().getId() : null)
                .employeeName(user.getEmployee() != null ? user.getEmployee().getName() : null)
                .employeeCode(user.getEmployee() != null ? user.getEmployee().getEmployeeCode() : null)
                .mustChangePassword(user.isMustChangePassword())
                .lastLogin(user.getLastLogin())
                .allStoresAccess(RolePermissions.has(user.getRole(), Permission.STORE_ACCESS_ALL))
                .currentStoreId(user.getCurrentStore() != null ? user.getCurrentStore().getId() : null)
                .currentStoreName(user.getCurrentStore() != null ? user.getCurrentStore().getStoreName() : null)
                .currentStoreCode(user.getCurrentStore() != null ? user.getCurrentStore().getStoreCode() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
