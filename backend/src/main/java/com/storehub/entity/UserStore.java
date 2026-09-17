package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A User's explicit access to one Store (Multi-Store spec section 11, ASSIGNED_STORES
 * mode). A user with no rows here and no STORE_ACCESS_ALL permission has no store access
 * at all — {@code StoreAccessService} is the only place this is ever interpreted, never
 * checked ad hoc in a controller. A user with exactly one row is functionally
 * SINGLE_STORE; ALL_STORES users (ADMIN by default) don't need rows here since their
 * access is granted by the STORE_ACCESS_ALL permission instead.
 */
@Entity
@Table(name = "user_stores", uniqueConstraints = @UniqueConstraint(name = "uk_user_store", columnNames = {"user_id", "store_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
