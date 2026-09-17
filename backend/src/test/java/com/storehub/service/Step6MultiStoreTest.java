package com.storehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storehub.dto.StockTransferItemRequest;
import com.storehub.dto.StockTransferRequest;
import com.storehub.dto.StockTransferResponse;
import com.storehub.dto.StoreGstContextResponse;
import com.storehub.dto.StoreRequest;
import com.storehub.dto.StoreResponse;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.Role;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.StockTransferStatus;
import com.storehub.entity.Store;
import com.storehub.entity.StoreType;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.StoreRepository;
import com.storehub.repository.UserRepository;
import com.storehub.security.JwtUtil;
import com.storehub.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StoreHub Step 6 "Multi-Store / Multi-Branch" test matrix — creation/uniqueness, the
 * ALL_STORES vs ASSIGNED_STORES access model centralized in {@link StoreAccessService},
 * per-store inventory isolation, the full Stock Transfer workflow (with its stock
 * movement and cancellation-window rules), the Default Store migration anchor, and
 * store-level GST context resolution. Service-layer scenarios that need a specific
 * authenticated identity set {@link SecurityContextHolder} directly with a
 * {@link UserPrincipal} (mirroring how {@code JwtAuthenticationFilter} populates it on
 * a real request), since {@code @Transactional} rollback does not touch that ThreadLocal;
 * cleared in {@link #clearSecurityContext()} after every test. Pure-authorization 403
 * scenarios use MockMvc with a real JWT, following the {@code Step5UserRoleTest} convention.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Step6MultiStoreTest {

    @Autowired
    private StoreService storeService;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private StoreAccessService storeAccessService;
    @Autowired
    private StockTransferService stockTransferService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProductRepository productRepository;
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- test fixtures ----

    private Store newStore(String namePrefix) {
        StoreRequest req = new StoreRequest();
        req.setStoreCode(storeService.generateCode());
        req.setStoreName(namePrefix + " " + counter++);
        req.setStoreType(StoreType.STORE);
        StoreResponse created = storeService.create(req);
        return storeRepository.findById(created.getId()).orElseThrow();
    }

    private User newUser(Role role) {
        String email = "step6user" + (counter++) + "@storehub.test";
        return userRepository.save(User.builder()
                .firstName("Step6")
                .lastName("User")
                .email(email)
                .mobile("90000" + String.valueOf(counter++).substring(0, 5))
                .password(passwordEncoder.encode("Password123"))
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Product newProduct() {
        return productRepository.save(Product.builder()
                .name("Step6 Item " + counter++)
                .sellingPrice(java.math.BigDecimal.TEN)
                .purchasePrice(java.math.BigDecimal.ONE)
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private void loginAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }

    private void assignStore(User user, Store store) {
        storeAccessService.assignStores(user.getId(), Set.of(store.getId()));
    }

    // ---- Scenario 1: duplicate store code rejected ----
    @Test
    void scenario1_duplicateStoreCode_rejected() {
        StoreRequest req = new StoreRequest();
        String code = "ST-T6-" + counter++;
        req.setStoreCode(code);
        req.setStoreName("Duplicate Code Store");
        req.setStoreType(StoreType.STORE);
        storeService.create(req);

        StoreRequest dupe = new StoreRequest();
        dupe.setStoreCode(code);
        dupe.setStoreName("Another Store");
        dupe.setStoreType(StoreType.STORE);
        assertThatThrownBy(() -> storeService.create(dupe))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(code);
    }

    // ---- Scenario 2: auto-generated store codes are unique across successive calls ----
    @Test
    void scenario2_autoGeneratedStoreCode_isUnique() {
        String code1 = storeService.generateCode();
        StoreRequest req1 = new StoreRequest();
        req1.setStoreCode(code1);
        req1.setStoreName("Auto Code Store 1");
        req1.setStoreType(StoreType.STORE);
        storeService.create(req1);

        String code2 = storeService.generateCode();
        assertThat(code2).isNotEqualTo(code1);
    }

    // ---- Scenario 3: ADMIN (STORE_ACCESS_ALL) sees every store without explicit assignment rows ----
    @Test
    void scenario3_adminHasAllStoresAccess_viaStoreAccessAllPermission() {
        Store storeA = newStore("Admin-Visible-A");
        Store storeB = newStore("Admin-Visible-B");
        User admin = newUser(Role.ADMIN);

        assertThat(storeAccessService.hasAllStoresAccess(admin)).isTrue();
        assertThat(storeAccessService.hasStoreAccess(admin, storeA.getId())).isTrue();
        assertThat(storeAccessService.hasStoreAccess(admin, storeB.getId())).isTrue();
        assertThat(storeAccessService.getAccessibleStoreIds(admin)).contains(storeA.getId(), storeB.getId());
    }

    // ---- Scenario 4: an ASSIGNED_STORES user is denied access to a store they were never assigned ----
    @Test
    void scenario4_assignedStoresUser_deniedAccessToUnassignedStore() {
        Store assigned = newStore("Assigned-Store");
        Store other = newStore("Unassigned-Store");
        User inventoryUser = newUser(Role.INVENTORY_USER);
        assignStore(inventoryUser, assigned);

        assertThat(storeAccessService.hasStoreAccess(inventoryUser, assigned.getId())).isTrue();
        assertThat(storeAccessService.hasStoreAccess(inventoryUser, other.getId())).isFalse();
        assertThatThrownBy(() -> storeAccessService.assertStoreAccess(inventoryUser, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Scenario 5: resolveEffectiveStoreId with no explicit store and no current-store selection throws ----
    @Test
    void scenario5_resolveEffectiveStoreId_noStoreSelected_throws() {
        User user = newUser(Role.INVENTORY_USER);
        assertThatThrownBy(() -> storeAccessService.resolveEffectiveStoreId(user, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A store must be selected");
    }

    // ---- Scenario 6: resolveEffectiveStoreId validates an explicitly requested store against the caller's own access ----
    @Test
    void scenario6_resolveEffectiveStoreId_explicitStore_validatedAgainstAccess() {
        Store assigned = newStore("Effective-Assigned");
        Store other = newStore("Effective-Other");
        User user = newUser(Role.INVENTORY_USER);
        assignStore(user, assigned);

        assertThat(storeAccessService.resolveEffectiveStoreId(user, assigned.getId())).isEqualTo(assigned.getId());
        assertThatThrownBy(() -> storeAccessService.resolveEffectiveStoreId(user, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Scenario 7: resolveViewableStoreId for an ALL_STORES caller with no explicit request is null (unfiltered/aggregate) ----
    @Test
    void scenario7_resolveViewableStoreId_allStoresUser_getsNullForUnfiltered() {
        User admin = newUser(Role.ADMIN);
        assertThat(storeAccessService.resolveViewableStoreId(admin, null)).isNull();
    }

    // ---- Scenario 8: resolveViewableStoreId auto-narrows a store-scoped user with exactly one accessible store ----
    @Test
    void scenario8_resolveViewableStoreId_singleAssignedStore_defaultsAutomatically() {
        Store onlyStore = newStore("Single-Assigned");
        User user = newUser(Role.INVENTORY_USER);
        assignStore(user, onlyStore);

        assertThat(storeAccessService.resolveViewableStoreId(user, null)).isEqualTo(onlyStore.getId());
    }

    // ---- Scenario 9: resolveViewableStoreId with multiple assigned stores and no selection forces a choice ----
    @Test
    void scenario9_resolveViewableStoreId_multipleAssignedStores_noSelection_throws() {
        Store storeA = newStore("Multi-A");
        Store storeB = newStore("Multi-B");
        User user = newUser(Role.INVENTORY_USER);
        storeAccessService.assignStores(user.getId(), Set.of(storeA.getId(), storeB.getId()));

        assertThatThrownBy(() -> storeAccessService.resolveViewableStoreId(user, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("select a store");
    }

    // ---- Scenario 10: assignStores has replace-all semantics and clears a current-store selection that falls outside it ----
    @Test
    void scenario10_assignStores_replaceAll_clearsStaleCurrentStore() {
        Store storeA = newStore("Replace-A");
        Store storeB = newStore("Replace-B");
        User user = newUser(Role.INVENTORY_USER);
        assignStore(user, storeA);

        user.setCurrentStore(storeA);
        userRepository.save(user);

        storeAccessService.assignStores(user.getId(), Set.of(storeB.getId()));

        assertThat(storeAccessService.getAssignedStoreIds(user.getId())).containsExactly(storeB.getId());
        assertThat(storeAccessService.hasStoreAccess(user, storeA.getId())).isFalse();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStore()).isNull();
    }

    // ---- Scenario 11: the same product's stock is tracked independently per store ----
    @Test
    void scenario11_inventoryIsolation_sameProductDifferentStores_independentStock() {
        Store storeA = newStore("Isolation-A");
        Store storeB = newStore("Isolation-B");
        Product product = newProduct();

        inventoryService.applyMovement(product.getId(), storeA.getId(), 50, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "seed store A");
        inventoryService.applyMovement(product.getId(), storeB.getId(), 20, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "seed store B");

        assertThat(inventoryService.getCurrentStock(product.getId(), storeA.getId())).isEqualTo(50);
        assertThat(inventoryService.getCurrentStock(product.getId(), storeB.getId())).isEqualTo(20);

        inventoryService.applyMovement(product.getId(), storeA.getId(), -30, StockMovementType.STOCK_OUT,
                ReferenceType.MANUAL, null, "sell from store A");

        assertThat(inventoryService.getCurrentStock(product.getId(), storeA.getId())).isEqualTo(20);
        assertThat(inventoryService.getCurrentStock(product.getId(), storeB.getId())).isEqualTo(20);
    }

    // ---- Scenario 12: full stock transfer workflow moves stock from source to destination store ----
    @Test
    void scenario12_stockTransfer_fullWorkflow_movesStockBetweenStores() {
        Store fromStore = newStore("Transfer-From");
        Store toStore = newStore("Transfer-To");
        Product product = newProduct();
        User admin = newUser(Role.ADMIN);
        loginAs(admin);

        inventoryService.applyMovement(product.getId(), fromStore.getId(), 100, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "seed source store");

        StockTransferItemRequest itemReq = new StockTransferItemRequest();
        itemReq.setProductId(product.getId());
        itemReq.setQuantity(40);

        StockTransferRequest req = new StockTransferRequest();
        req.setTransferDate(LocalDate.now());
        req.setFromStoreId(fromStore.getId());
        req.setToStoreId(toStore.getId());
        req.setItems(List.of(itemReq));

        StockTransferResponse created = stockTransferService.create(req);
        assertThat(created.getStatus()).isEqualTo(StockTransferStatus.DRAFT);
        // Stock has not moved yet at DRAFT.
        assertThat(inventoryService.getCurrentStock(product.getId(), fromStore.getId())).isEqualTo(100);
        assertThat(inventoryService.getCurrentStock(product.getId(), toStore.getId())).isEqualTo(0);

        StockTransferResponse approved = stockTransferService.approve(created.getId());
        assertThat(approved.getStatus()).isEqualTo(StockTransferStatus.APPROVED);
        // Still no stock movement at APPROVED — purely an authorization stage.
        assertThat(inventoryService.getCurrentStock(product.getId(), fromStore.getId())).isEqualTo(100);

        StockTransferResponse dispatched = stockTransferService.dispatch(created.getId());
        assertThat(dispatched.getStatus()).isEqualTo(StockTransferStatus.DISPATCHED);
        assertThat(inventoryService.getCurrentStock(product.getId(), fromStore.getId())).isEqualTo(60);
        assertThat(inventoryService.getCurrentStock(product.getId(), toStore.getId())).isEqualTo(0);

        StockTransferResponse received = stockTransferService.receive(created.getId());
        assertThat(received.getStatus()).isEqualTo(StockTransferStatus.RECEIVED);
        assertThat(inventoryService.getCurrentStock(product.getId(), fromStore.getId())).isEqualTo(60);
        assertThat(inventoryService.getCurrentStock(product.getId(), toStore.getId())).isEqualTo(40);
    }

    // ---- Scenario 13: a DISPATCHED transfer (stock already moved) can no longer be cancelled ----
    @Test
    void scenario13_stockTransfer_cancelAfterDispatch_rejected() {
        Store fromStore = newStore("Cancel-From");
        Store toStore = newStore("Cancel-To");
        Product product = newProduct();
        User admin = newUser(Role.ADMIN);
        loginAs(admin);

        inventoryService.applyMovement(product.getId(), fromStore.getId(), 10, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "seed");

        StockTransferItemRequest itemReq = new StockTransferItemRequest();
        itemReq.setProductId(product.getId());
        itemReq.setQuantity(5);
        StockTransferRequest req = new StockTransferRequest();
        req.setTransferDate(LocalDate.now());
        req.setFromStoreId(fromStore.getId());
        req.setToStoreId(toStore.getId());
        req.setItems(List.of(itemReq));

        StockTransferResponse created = stockTransferService.create(req);
        stockTransferService.approve(created.getId());
        stockTransferService.dispatch(created.getId());

        assertThatThrownBy(() -> stockTransferService.cancel(created.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be cancelled");

        // A DRAFT transfer, by contrast, cancels cleanly.
        StockTransferResponse draftOnly = stockTransferService.create(req);
        StockTransferResponse cancelled = stockTransferService.cancel(draftOnly.getId());
        assertThat(cancelled.getStatus()).isEqualTo(StockTransferStatus.CANCELLED);
    }

    // ---- Scenario 14: a user with access to neither the source nor destination store cannot view the transfer (IDOR) ----
    @Test
    void scenario14_stockTransfer_crossStoreIDOR_deniedForUnassignedUser() {
        Store fromStore = newStore("IDOR-From");
        Store toStore = newStore("IDOR-To");
        Store unrelatedStore = newStore("IDOR-Unrelated");
        Product product = newProduct();
        User admin = newUser(Role.ADMIN);
        loginAs(admin);

        inventoryService.applyMovement(product.getId(), fromStore.getId(), 10, StockMovementType.STOCK_IN,
                ReferenceType.MANUAL, null, "seed");

        StockTransferItemRequest itemReq = new StockTransferItemRequest();
        itemReq.setProductId(product.getId());
        itemReq.setQuantity(5);
        StockTransferRequest req = new StockTransferRequest();
        req.setTransferDate(LocalDate.now());
        req.setFromStoreId(fromStore.getId());
        req.setToStoreId(toStore.getId());
        req.setItems(List.of(itemReq));
        StockTransferResponse created = stockTransferService.create(req);

        User outsider = newUser(Role.INVENTORY_USER);
        assignStore(outsider, unrelatedStore);
        loginAs(outsider);

        assertThatThrownBy(() -> stockTransferService.getById(created.getId()))
                .isInstanceOf(AccessDeniedException.class);

        // Also cannot create a transfer FROM a store they are not assigned to.
        StockTransferRequest deniedCreate = new StockTransferRequest();
        deniedCreate.setTransferDate(LocalDate.now());
        deniedCreate.setFromStoreId(fromStore.getId());
        deniedCreate.setToStoreId(toStore.getId());
        deniedCreate.setItems(List.of(itemReq));
        assertThatThrownBy(() -> stockTransferService.create(deniedCreate))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Scenario 15: 403 on unauthorized - INVENTORY_USER (no STOCK_TRANSFER_APPROVE) calling the approve endpoint ----
    @Test
    void scenario15_stockTransfer_inventoryUser_cannotApprove_forbidden() throws Exception {
        User inventoryUser = newUser(Role.INVENTORY_USER);

        mockMvc.perform(patch("/api/stock-transfers/999999999/approve")
                        .header("Authorization", "Bearer " + tokenFor(inventoryUser)))
                .andExpect(status().isForbidden());
    }

    // ---- Scenario 16: Default Store creation is idempotent — the single historical-data anchor is never duplicated ----
    @Test
    void scenario16_defaultStoreMigration_idempotent() {
        Store first = storeService.getOrCreateDefaultStore();
        Store second = storeService.getOrCreateDefaultStore();

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStoreCode()).isEqualTo(StoreService.DEFAULT_STORE_CODE);
        assertThat(storeRepository.findAll().stream()
                .filter(s -> StoreService.DEFAULT_STORE_CODE.equalsIgnoreCase(s.getStoreCode())).count()).isEqualTo(1);
    }

    // ---- Scenario 17: a store's own GSTIN overrides the business-wide GST config as the seller context ----
    @Test
    void scenario17_storeGstContext_ownGstinOverridesBusinessConfig() {
        Store storeWithGstin = newStore("GST-Override-Store");
        storeWithGstin.setGstin("27AAAAA0000A1Z5");
        storeRepository.save(storeWithGstin);

        Store storeWithoutGstin = newStore("GST-Fallback-Store");

        StoreGstContextResponse withOverride = storeService.resolveGstContext(storeWithGstin.getId());
        assertThat(withOverride.getSource()).isEqualTo("STORE");
        assertThat(withOverride.getGstin()).isEqualTo("27AAAAA0000A1Z5");

        StoreGstContextResponse withFallback = storeService.resolveGstContext(storeWithoutGstin.getId());
        assertThat(withFallback.getSource()).isEqualTo("BUSINESS");
    }
}
