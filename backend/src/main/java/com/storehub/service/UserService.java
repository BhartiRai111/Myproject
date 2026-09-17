package com.storehub.service;

import com.storehub.dto.AdminPasswordResetRequest;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.PasswordChangeRequest;
import com.storehub.dto.StoreResponse;
import com.storehub.dto.UserCreateRequest;
import com.storehub.dto.UserResponse;
import com.storehub.dto.UserStatusUpdateRequest;
import com.storehub.dto.UserUpdateRequest;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Employee;
import com.storehub.entity.Permission;
import com.storehub.entity.Role;
import com.storehub.entity.Store;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.DuplicateEmailException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.exception.UserNotFoundException;
import com.storehub.repository.EmployeeRepository;
import com.storehub.repository.StoreRepository;
import com.storehub.repository.UserRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Never physically deletes a user (spec section 52) — only ever ACTIVE/INACTIVE, so
 * historical audit/transaction references stay valid. Admin-safety (spec sections 44-45)
 * is enforced here, not in the controller: the system must never be left with zero active
 * ADMIN users, whether by role change or deactivation.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PagedResponse<UserResponse> getUsers(String search, Role role, UserStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserResponse> result = userRepository.search(search, role, status, pageable)
                .map(UserResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(findUserOrThrow(id));
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        Employee employee = resolveEmployee(request.getEmployeeId(), null);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .employee(employee)
                .build();

        User saved = userRepository.save(user);
        auditService.log(AuditAction.CREATE, "ADMIN", "User", saved.getId(), null,
                null, null, "User " + saved.getEmail() + " created with role " + saved.getRole());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        Employee employee = resolveEmployee(request.getEmployeeId(), id);

        if (user.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN) {
            guardNotLastActiveAdmin(user, "Cannot change the role of the last active ADMIN. Promote another user to ADMIN first.");
        }
        if (user.getRole() == Role.ADMIN && request.getStatus() == UserStatus.INACTIVE) {
            guardNotLastActiveAdmin(user, "Cannot deactivate the last active ADMIN. Promote another user to ADMIN first.");
        }

        String oldRole = user.getRole().name();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setEmployee(employee);

        User saved = userRepository.save(user);
        if (!oldRole.equals(saved.getRole().name())) {
            auditService.log(AuditAction.PERMISSION_CHANGE, "ADMIN", "User", saved.getId(), null,
                    oldRole, saved.getRole().name(), "User " + saved.getEmail() + " role changed from " + oldRole + " to " + saved.getRole());
        } else {
            auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
                    null, null, "User " + saved.getEmail() + " updated");
        }
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatusUpdateRequest request) {
        User user = findUserOrThrow(id);
        if (user.getRole() == Role.ADMIN && request.getStatus() == UserStatus.INACTIVE) {
            guardNotLastActiveAdmin(user, "Cannot deactivate the last active ADMIN. Promote another user to ADMIN first.");
        }
        UserStatus oldStatus = user.getStatus();
        user.setStatus(request.getStatus());
        User saved = userRepository.save(user);
        auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
                oldStatus.name(), saved.getStatus().name(), "User " + saved.getEmail() + " status changed to " + saved.getStatus());
        return UserResponse.fromEntity(saved);
    }

    /** ADMIN resets another user's password — never requires or reveals the existing one; forces a change at next login. */
    @Transactional
    public UserResponse adminResetPassword(Long id, AdminPasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        User user = findUserOrThrow(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        // Never log the password itself — only that a reset happened.
        auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
                null, null, "Password reset for user " + saved.getEmail() + " by an administrator");
        return UserResponse.fromEntity(saved);
    }

    /** Self-service password change — requires the current password, unlike an admin reset. */
    @Transactional
    public void changeOwnPassword(String currentUserEmail, PasswordChangeRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        auditService.log(AuditAction.UPDATE, "ADMIN", "User", user.getId(), null,
                null, null, "User " + user.getEmail() + " changed their own password");
    }

    public java.util.Set<Permission> effectivePermissions(Long id) {
        User user = findUserOrThrow(id);
        return RolePermissions.forRole(user.getRole());
    }

    /** ADMIN assigns a user's ASSIGNED_STORES list (spec sections 11, 70) — replace-all semantics. */
    @Transactional
    public UserResponse assignStores(Long id, Set<Long> storeIds) {
        User user = findUserOrThrow(id);
        storeAccessService.assignStores(id, storeIds);
        auditService.log(AuditAction.UPDATE, "ADMIN", "User", id, null,
                null, null, "Store access for user " + user.getEmail() + " set to " + storeIds.size() + " store(s)");
        return UserResponse.fromEntity(findUserOrThrow(id));
    }

    public List<Long> getAssignedStoreIds(Long id) {
        findUserOrThrow(id);
        return storeAccessService.getAssignedStoreIds(id);
    }

    /** Self-service current-store switch (spec section 12/45) — validated against the caller's own store access, never trusted blindly. */
    @Transactional
    public UserResponse setCurrentStore(String currentUserEmail, Long storeId) {
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestException("User not found"));
        storeAccessService.assertStoreAccess(user, storeId);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new MasterNotFoundException("Store", storeId));
        user.setCurrentStore(store);
        User saved = userRepository.save(user);
        auditService.log(AuditAction.UPDATE, "AUTH", "User", saved.getId(), store.getStoreCode(),
                null, null, "User " + saved.getEmail() + " switched current store to " + store.getStoreCode());
        return UserResponse.fromEntity(saved);
    }

    /** Every store this user may act on — ALL stores for an ALL_STORES user, else their explicit assignments (spec section 55). */
    public List<StoreResponse> getAccessibleStores(String currentUserEmail) {
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestException("User not found"));
        List<Long> ids = storeAccessService.getAccessibleStoreIds(user);
        return storeRepository.findAllById(ids).stream().map(StoreResponse::fromEntity).toList();
    }

    private Employee resolveEmployee(Long employeeId, Long excludeUserId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new MasterNotFoundException("Employee", employeeId));
        boolean alreadyLinked = excludeUserId != null
                ? userRepository.existsByEmployeeIdAndIdNot(employeeId, excludeUserId)
                : userRepository.existsByEmployeeId(employeeId);
        if (alreadyLinked) {
            throw new BadRequestException("Employee '" + employee.getName() + "' is already linked to another user login");
        }
        return employee;
    }

    /** Throws if {@code user} is the only ACTIVE ADMIN in the system (spec sections 44-45). */
    private void guardNotLastActiveAdmin(User user, String message) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        long activeAdmins = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (activeAdmins <= 1) {
            throw new BadRequestException(message);
        }
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
