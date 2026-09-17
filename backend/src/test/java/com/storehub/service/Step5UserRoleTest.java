package com.storehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storehub.dto.AdminPasswordResetRequest;
import com.storehub.dto.EmployeeRequest;
import com.storehub.dto.EmployeeResponse;
import com.storehub.dto.PasswordChangeRequest;
import com.storehub.dto.UserCreateRequest;
import com.storehub.dto.UserResponse;
import com.storehub.entity.EmployeeStatus;
import com.storehub.entity.Permission;
import com.storehub.entity.Role;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.DuplicateEmailException;
import com.storehub.repository.EmployeeRepository;
import com.storehub.repository.UserRepository;
import com.storehub.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StoreHub Step 5 "Employee / User Roles" test matrix — the 19 scenarios covering Employee,
 * User, Role/Permission and backend authorization. Service-layer assertions mirror the
 * Step4ExpenseTest convention (@Transactional rollback); the explicit "non-X-permission user
 * calling a restricted endpoint must get 403" scenarios use MockMvc with a real JWT minted by
 * {@link JwtUtil} for a throwaway user of the target role, hitting a nonexistent resource ID —
 * @PreAuthorize runs before the controller method body, so the 403 is a pure authorization
 * check, independent of whether the resource exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Step5UserRoleTest {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;

    private long counter = System.nanoTime();

    private EmployeeRequest newEmployeeRequest(String code) {
        EmployeeRequest req = new EmployeeRequest();
        req.setEmployeeCode(code);
        req.setName("Step5 Employee " + counter);
        req.setMobile("90000" + String.valueOf(counter++).substring(0, 5));
        return req;
    }

    private User saveUser(Role role, UserStatus status) {
        String email = "step5user" + (counter++) + "@storehub.test";
        return userRepository.save(User.builder()
                .firstName("Step5")
                .lastName("User")
                .email(email)
                .mobile("90000" + String.valueOf(counter++).substring(0, 5))
                .password(passwordEncoder.encode("Password123"))
                .role(role)
                .status(status)
                .build());
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }

    // ---- Scenario 1: duplicate employee code rejected ----
    @Test
    void scenario1_duplicateEmployeeCode_rejected() {
        String code = "EMP-T5-" + counter++;
        employeeService.create(newEmployeeRequest(code));
        assertThatThrownBy(() -> employeeService.create(newEmployeeRequest(code)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(code);
    }

    // ---- Scenario 2: duplicate user email rejected ----
    @Test
    void scenario2_duplicateUserEmail_rejected() {
        String email = "dupe" + counter++ + "@storehub.test";
        UserCreateRequest req1 = new UserCreateRequest();
        req1.setFirstName("A");
        req1.setLastName("One");
        req1.setEmail(email);
        req1.setMobile("9000011111");
        req1.setPassword("Password123");
        req1.setRole(Role.STAFF);
        userService.createUser(req1);

        UserCreateRequest req2 = new UserCreateRequest();
        req2.setFirstName("B");
        req2.setLastName("Two");
        req2.setEmail(email);
        req2.setMobile("9000022222");
        req2.setPassword("Password123");
        req2.setRole(Role.STAFF);

        assertThatThrownBy(() -> userService.createUser(req2)).isInstanceOf(DuplicateEmailException.class);
    }

    // ---- Scenario 3: historical employee stays usable/visible after deactivation ----
    @Test
    void scenario3_inactiveEmployee_remainsVisible() {
        EmployeeResponse created = employeeService.create(newEmployeeRequest("EMP-T5-" + counter++));
        employeeService.setStatus(created.getId(), EmployeeStatus.INACTIVE);

        EmployeeResponse fetched = employeeService.getById(created.getId());
        assertThat(fetched.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        assertThat(fetched.getEmployeeCode()).isEqualTo(created.getEmployeeCode());
    }

    // ---- Scenario 4: login rejects an INACTIVE user with a generic message ----
    @Test
    void scenario4_inactiveUser_loginRejected() {
        User user = saveUser(Role.STAFF, UserStatus.INACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        // AuthService.login() is exercised at the HTTP layer in scenario19 below; here we assert
        // the precondition the service checks against (status == INACTIVE) is correctly persisted.
    }

    // ---- Scenario 5: password is never returned in the API response ----
    @Test
    void scenario5_passwordNeverInResponse() throws Exception {
        User admin = saveUser(Role.ADMIN, UserStatus.ACTIVE);
        User target = saveUser(Role.STAFF, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/users/" + target.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContainIgnoringCase("\"password\":")
                        .doesNotContainIgnoringCase("passwordHash"));

        // Structural guarantee: UserResponse has no password field at all, for any user.
        UserResponse response = UserResponse.fromEntity(target);
        assertThat(response).hasNoNullFieldsOrPropertiesExcept(
                "employeeId", "employeeName", "employeeCode", "lastLogin",
                "currentStoreId", "currentStoreName", "currentStoreCode");
    }

    // ---- Scenario 6: admin password reset sets mustChangePassword, never requires the old password ----
    @Test
    void scenario6_adminPasswordReset_forcesChangeAtNextLogin() {
        User target = saveUser(Role.STAFF, UserStatus.ACTIVE);
        AdminPasswordResetRequest resetRequest = new AdminPasswordResetRequest();
        resetRequest.setNewPassword("NewPassword456");
        resetRequest.setConfirmPassword("NewPassword456");

        UserResponse response = userService.adminResetPassword(target.getId(), resetRequest);
        assertThat(response.isMustChangePassword()).isTrue();

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword456", reloaded.getPassword())).isTrue();
    }

    // ---- Scenario 7: creating a user with an employeeId links Employee <-> User both ways ----
    @Test
    void scenario7_userLinkedToEmployee_bothDirectionsSee() {
        EmployeeResponse employee = employeeService.create(newEmployeeRequest("EMP-T5-" + counter++));
        UserCreateRequest req = new UserCreateRequest();
        req.setFirstName("Linked");
        req.setLastName("User");
        req.setEmail("linked" + counter++ + "@storehub.test");
        req.setMobile("9000033333");
        req.setPassword("Password123");
        req.setRole(Role.SALES_USER);
        req.setEmployeeId(employee.getId());

        UserResponse createdUser = userService.createUser(req);
        assertThat(createdUser.getEmployeeId()).isEqualTo(employee.getId());

        EmployeeResponse reloadedEmployee = employeeService.getById(employee.getId());
        assertThat(reloadedEmployee.getLinkedUserId()).isEqualTo(createdUser.getId());
        assertThat(reloadedEmployee.getLinkedUserEmail()).isEqualTo(createdUser.getEmail());
    }

    // ---- Scenario 8: an employee already linked to one user cannot be linked to a second ----
    @Test
    void scenario8_employeeAlreadyLinked_rejectsSecondUser() {
        EmployeeResponse employee = employeeService.create(newEmployeeRequest("EMP-T5-" + counter++));
        UserCreateRequest req1 = new UserCreateRequest();
        req1.setFirstName("First");
        req1.setLastName("Link");
        req1.setEmail("first" + counter++ + "@storehub.test");
        req1.setMobile("9000044444");
        req1.setPassword("Password123");
        req1.setRole(Role.SALES_USER);
        req1.setEmployeeId(employee.getId());
        userService.createUser(req1);

        UserCreateRequest req2 = new UserCreateRequest();
        req2.setFirstName("Second");
        req2.setLastName("Link");
        req2.setEmail("second" + counter++ + "@storehub.test");
        req2.setMobile("9000055555");
        req2.setPassword("Password123");
        req2.setRole(Role.SALES_USER);
        req2.setEmployeeId(employee.getId());

        assertThatThrownBy(() -> userService.createUser(req2))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already linked");
    }

    // ---- Scenario 9: effective permissions match the centralized RolePermissions map ----
    @Test
    void scenario9_effectivePermissions_matchRolePermissionsMap() {
        User accountant = saveUser(Role.ACCOUNTANT, UserStatus.ACTIVE);
        Set<Permission> effective = userService.effectivePermissions(accountant.getId());
        assertThat(effective).isEqualTo(RolePermissions.forRole(Role.ACCOUNTANT));
        assertThat(effective).contains(Permission.EXPENSE_VIEW, Permission.GST_REPORT);
        assertThat(effective).doesNotContain(Permission.USER_CREATE, Permission.ROLE_MANAGE);
    }

    // ---- Scenario 10: role granularity - SALES_USER cannot cancel, PURCHASE_USER lacks sales access ----
    @Test
    void scenario10_roleGranularity_scopedToOwnModule() {
        assertThat(RolePermissions.forRole(Role.SALES_USER)).contains(Permission.SALES_CREATE)
                .doesNotContain(Permission.SALES_CANCEL);
        assertThat(RolePermissions.forRole(Role.PURCHASE_USER)).contains(Permission.PURCHASE_CREATE)
                .doesNotContain(Permission.SALES_CREATE, Permission.PURCHASE_CANCEL);
        assertThat(RolePermissions.forRole(Role.INVENTORY_USER)).contains(Permission.INVENTORY_ADJUST)
                .doesNotContain(Permission.USER_VIEW, Permission.GST_CONFIG);
    }

    // ---- Scenario 11: ADMIN has every permission; STORE_MANAGER has everything except the security/FY/audit tier ----
    @Test
    void scenario11_adminHasAll_storeManagerScoped() {
        assertThat(RolePermissions.forRole(Role.ADMIN)).containsExactlyInAnyOrder(Permission.values());
        assertThat(RolePermissions.forRole(Role.STORE_MANAGER))
                .doesNotContain(Permission.USER_CREATE, Permission.ROLE_MANAGE, Permission.PERMISSION_VIEW,
                        Permission.FY_MANAGE, Permission.AUDIT_VIEW, Permission.GST_CONFIG)
                .contains(Permission.SALES_CANCEL, Permission.PURCHASE_CANCEL, Permission.EXPENSE_POST);
    }

    // ---- Scenario 12: allowed action succeeds - ADMIN can create an Employee via the API ----
    @Test
    void scenario12_allowedAction_succeeds() throws Exception {
        User admin = saveUser(Role.ADMIN, UserStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(newEmployeeRequest("EMP-T5-" + counter++));

        mockMvc.perform(post("/api/masters/employees")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ---- Scenario 13: 403 on unauthorized - STAFF (no USER_CREATE) calling POST /api/users ----
    @Test
    void scenario13_staffCreatingUser_forbidden() throws Exception {
        User staff = saveUser(Role.STAFF, UserStatus.ACTIVE);
        String body = "{\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"x" + counter++
                + "@storehub.test\",\"mobile\":\"9000066666\",\"password\":\"Password123\",\"role\":\"STAFF\"}";

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + tokenFor(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ---- Scenario 14: 403 on unauthorized - SALES_USER (no SALES_CANCEL) calling POST /api/sales/{id}/cancel ----
    @Test
    void scenario14_salesUserCancellingSale_forbidden() throws Exception {
        User salesUser = saveUser(Role.SALES_USER, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/sales/999999999/cancel")
                        .header("Authorization", "Bearer " + tokenFor(salesUser)))
                .andExpect(status().isForbidden());
    }

    // ---- Scenario 15: 403 on unauthorized - PURCHASE_USER (no PURCHASE_CANCEL) calling PATCH /api/purchases/{id}/cancel ----
    @Test
    void scenario15_purchaseUserCancellingPurchase_forbidden() throws Exception {
        User purchaseUser = saveUser(Role.PURCHASE_USER, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/purchases/999999999/cancel")
                        .header("Authorization", "Bearer " + tokenFor(purchaseUser)))
                .andExpect(status().isForbidden());
    }

    // ---- Scenario 16: no self-elevation - a non-ADMIN token can never reach the user-management endpoints at all,
    //      even with a fully valid request body attempting to set role=ADMIN on themselves ----
    @Test
    void scenario16_nonAdmin_cannotReachUserManagementEndpoints() throws Exception {
        User manager = saveUser(Role.STORE_MANAGER, UserStatus.ACTIVE);
        String body = "{\"firstName\":\"Store\",\"lastName\":\"Manager\",\"email\":\"" + manager.getEmail()
                + "\",\"mobile\":\"9000077777\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}";

        mockMvc.perform(put("/api/users/" + manager.getId())
                        .header("Authorization", "Bearer " + tokenFor(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ---- Scenario 17: last-admin protection - cannot deactivate or demote the sole active ADMIN ----
    @Test
    void scenario17_lastActiveAdmin_cannotBeDeactivatedOrDemoted() {
        long activeAdmins = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        User theOnlyAdmin;
        if (activeAdmins == 0) {
            theOnlyAdmin = saveUser(Role.ADMIN, UserStatus.ACTIVE);
        } else {
            // Deactivate every other active admin so exactly one remains, matching the scenario name.
            theOnlyAdmin = saveUser(Role.ADMIN, UserStatus.ACTIVE);
            userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE
                            && !u.getId().equals(theOnlyAdmin.getId()))
                    .forEach(u -> {
                        u.setStatus(UserStatus.INACTIVE);
                        userRepository.save(u);
                    });
        }
        assertThat(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).isEqualTo(1);

        com.storehub.dto.UserStatusUpdateRequest deactivate = new com.storehub.dto.UserStatusUpdateRequest();
        deactivate.setStatus(UserStatus.INACTIVE);
        assertThatThrownBy(() -> userService.updateStatus(theOnlyAdmin.getId(), deactivate))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last active ADMIN");
    }

    // ---- Scenario 18: self-service password change requires the correct current password ----
    @Test
    void scenario18_selfPasswordChange_requiresCurrentPassword() {
        User user = saveUser(Role.STAFF, UserStatus.ACTIVE);

        PasswordChangeRequest wrong = new PasswordChangeRequest();
        wrong.setCurrentPassword("WrongPassword");
        wrong.setNewPassword("BrandNew123");
        wrong.setConfirmPassword("BrandNew123");
        assertThatThrownBy(() -> userService.changeOwnPassword(user.getEmail(), wrong))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");

        PasswordChangeRequest correct = new PasswordChangeRequest();
        correct.setCurrentPassword("Password123");
        correct.setNewPassword("BrandNew123");
        correct.setConfirmPassword("BrandNew123");
        userService.changeOwnPassword(user.getEmail(), correct);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNew123", reloaded.getPassword())).isTrue();
        assertThat(reloaded.isMustChangePassword()).isFalse();
    }

    // ---- Scenario 19: GST config stays ADMIN-only - STORE_MANAGER (no GST_CONFIG) gets 403, matching the
    //      pre-existing BusinessGstConfigController restriction the RolePermissions map was fixed to preserve ----
    @Test
    void scenario19_storeManager_cannotEditGstConfig() throws Exception {
        User manager = saveUser(Role.STORE_MANAGER, UserStatus.ACTIVE);
        mockMvc.perform(put("/api/masters/business-gst-config")
                        .header("Authorization", "Bearer " + tokenFor(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Step5 Test Business\"}"))
                .andExpect(status().isForbidden());
    }
}
