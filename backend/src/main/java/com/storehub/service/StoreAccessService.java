package com.storehub.service;

import com.storehub.entity.Permission;
import com.storehub.entity.Store;
import com.storehub.entity.User;
import com.storehub.entity.UserStore;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.StoreRepository;
import com.storehub.repository.UserRepository;
import com.storehub.repository.UserStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single centralized store-authorization service (Multi-Store spec section 13) —
 * every controller that needs to know "can this user touch this store's data" calls in
 * here rather than re-deriving the logic itself (spec section 13's explicit instruction
 * not to scatter store-access checks across dozens of controllers). Mirrors
 * {@code PermissionService}'s role as the one centralized authorization service from
 * Step 5.
 *
 * <p>Two access modes (spec section 11): ALL_STORES, granted by the
 * {@link Permission#STORE_ACCESS_ALL} permission (ADMIN by default — see
 * {@code RolePermissions}), and ASSIGNED_STORES, backed by explicit {@link UserStore}
 * rows. A user with neither has zero store access. Never trusts a frontend-supplied
 * storeId (spec section 14) — every read of "requested store" here is validated against
 * one of those two sources before use.
 */
@Service
@RequiredArgsConstructor
public class StoreAccessService {

    private final PermissionService permissionService;
    private final UserStoreRepository userStoreRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreService storeService;

    public boolean hasAllStoresAccess(User user) {
        return permissionService.hasPermission(user, Permission.STORE_ACCESS_ALL);
    }

    /** Every store id this user may act on — ALL active+inactive stores for an ALL_STORES user, else their explicit assignments. */
    public List<Long> getAccessibleStoreIds(User user) {
        if (user == null) {
            return List.of();
        }
        if (hasAllStoresAccess(user)) {
            return storeRepository.findAll().stream().map(Store::getId).collect(Collectors.toList());
        }
        return userStoreRepository.findByUserId(user.getId()).stream()
                .map(us -> us.getStore().getId())
                .collect(Collectors.toList());
    }

    /**
     * A null storeId means "no store on the record" — only possible on a row left over
     * from before the Default Store migration (spec section 76) ran. An ALL_STORES user
     * (e.g. ADMIN) can still see it; anyone store-scoped cannot, since it isn't actually
     * within any store they were assigned.
     */
    public boolean hasStoreAccess(User user, Long storeId) {
        if (user == null) {
            return false;
        }
        if (hasAllStoresAccess(user)) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        return userStoreRepository.existsByUserIdAndStoreId(user.getId(), storeId);
    }

    /** Throws 403 if the user cannot act on this store — the one place that decision is ever made. */
    public void assertStoreAccess(User user, Long storeId) {
        if (!hasStoreAccess(user, storeId)) {
            throw new AccessDeniedException("You do not have access to this store");
        }
    }

    /**
     * Resolves which store a new transaction belongs to (spec section 12/14): an
     * explicitly requested store is validated against the user's access; with none
     * requested, falls back to the user's current-store selection; with neither, the
     * caller must choose one. A null user (no authenticated request in context — a
     * system-triggered call or a service-layer test, never a real HTTP request, since
     * the JWT filter guarantees a non-null user by the time this runs) falls back to
     * the Default Store, mirroring {@link InventoryService}'s identical transitional
     * fallback for call sites that do not yet carry their own storeId (spec section 76's
     * historical-data-safety anchor) — this never weakens the "a store must be selected"
     * guarantee for an authenticated caller with no current store, which still throws.
     */
    public Long resolveEffectiveStoreId(User user, Long requestedStoreId) {
        if (requestedStoreId != null) {
            assertStoreAccess(user, requestedStoreId);
            return requestedStoreId;
        }
        if (user == null) {
            return storeService.getOrCreateDefaultStore().getId();
        }
        if (user.getCurrentStore() != null) {
            assertStoreAccess(user, user.getCurrentStore().getId());
            return user.getCurrentStore().getId();
        }
        throw new BadRequestException("A store must be selected for this transaction");
    }

    /** Replace-all semantics: the given set becomes this user's entire ASSIGNED_STORES list. */
    @Transactional
    public void assignStores(Long userId, Set<Long> storeIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.storehub.exception.UserNotFoundException(userId));
        userStoreRepository.deleteByUserId(userId);
        for (Long storeId : storeIds) {
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new MasterNotFoundException("Store", storeId));
            userStoreRepository.save(UserStore.builder().user(user).store(store).build());
        }
        // If the user's current store fell outside the new assignment (and they aren't ALL_STORES), clear it
        // rather than leave a stale selection the user can no longer actually use.
        if (user.getCurrentStore() != null && !hasStoreAccess(user, user.getCurrentStore().getId())) {
            user.setCurrentStore(null);
            userRepository.save(user);
        }
    }

    public List<Long> getAssignedStoreIds(Long userId) {
        return userStoreRepository.findByUserId(userId).stream()
                .map(us -> us.getStore().getId())
                .collect(Collectors.toList());
    }

    /**
     * Resolves the storeId a list/summary/export view should filter by (spec section 14) —
     * the single choke point every store-aware read endpoint calls, so store scoping is
     * never re-derived ad hoc per controller. An explicit request is validated against the
     * caller's own access (403 if not accessible); with none given, an ALL_STORES caller
     * gets {@code null} (meaning "aggregate across every store"). A store-scoped caller can
     * never fall through to that unfiltered view merely by omitting the parameter: they are
     * narrowed to their current store, or their one accessible store when they have exactly
     * one, and otherwise must pick one explicitly.
     */
    public Long resolveViewableStoreId(User user, Long requestedStoreId) {
        if (requestedStoreId != null) {
            assertStoreAccess(user, requestedStoreId);
            return requestedStoreId;
        }
        if (user == null || hasAllStoresAccess(user)) {
            return null;
        }
        if (user.getCurrentStore() != null) {
            return user.getCurrentStore().getId();
        }
        List<Long> accessible = getAccessibleStoreIds(user);
        if (accessible.size() == 1) {
            return accessible.get(0);
        }
        throw new BadRequestException(accessible.isEmpty() ? "You do not have access to any store" : "Please select a store");
    }
}
