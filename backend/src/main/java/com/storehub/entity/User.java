package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /**
     * Optional link to the business/person master record this login belongs to (spec
     * section 7). Nullable: a technical/admin account need not have an Employee record,
     * and an Employee may exist with no login at all. At most one User per Employee
     * (enforced by the unique constraint on this column, checked explicitly in
     * UserService before save so the failure is a clean 400, not a raw DB exception).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * The active store this user is currently working in (Multi-Store spec section 12) —
     * validated against {@link UserStore} access (or STORE_ACCESS_ALL) by
     * {@code StoreAccessService} before it is ever set, never trusted blindly from a
     * frontend request. Nullable: a brand-new user has no store selected until one is
     * assigned/chosen; an ALL_STORES admin need not have one at all.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_store_id")
    private Store currentStore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
