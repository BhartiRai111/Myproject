package com.storehub.service;

import com.storehub.dto.BusinessGstConfigResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StoreGstContextResponse;
import com.storehub.dto.StoreRequest;
import com.storehub.dto.StoreResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.City;
import com.storehub.entity.Country;
import com.storehub.entity.Store;
import com.storehub.entity.StoreStatus;
import com.storehub.entity.StoreType;
import com.storehub.entity.State;
import com.storehub.entity.Zone;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Store/Branch master (Multi-Store spec section 4). Never physically deletes a store
 * (spec section 57) — status is set to INACTIVE instead, so historical transactions
 * referencing it remain valid; deactivation itself never touches those historical rows.
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final CountryService countryService;
    private final StateService stateService;
    private final CityService cityService;
    private final ZoneService zoneService;
    private final StoreCodeGeneratorService storeCodeGeneratorService;
    private final AuditService auditService;
    private final BusinessGstConfigService businessGstConfigService;

    public PagedResponse<StoreResponse> search(String search, StoreStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("storeName").ascending());
        Page<StoreResponse> result = storeRepository.search(search, status, pageable).map(StoreResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public StoreResponse getById(Long id) {
        return StoreResponse.fromEntity(findOrThrow(id));
    }

    /** The ONLY place an auto-generated store code is produced (spec section 5) — a manually-entered code bypasses this. */
    public String generateCode() {
        return storeCodeGeneratorService.generateNext();
    }

    @Transactional
    public StoreResponse create(StoreRequest request) {
        if (storeRepository.existsByStoreCodeIgnoreCase(request.getStoreCode())) {
            throw new BadRequestException("A store with code '" + request.getStoreCode() + "' already exists");
        }
        if (StringUtils.hasText(request.getGstin()) && !com.storehub.util.GstinValidator.isValid(request.getGstin())) {
            throw new BadRequestException("GSTIN format is invalid");
        }

        Store store = Store.builder()
                .storeCode(request.getStoreCode())
                .storeName(request.getStoreName())
                .storeType(request.getStoreType())
                .legalName(request.getLegalName())
                .address(request.getAddress())
                .country(resolveCountry(request.getCountryId()))
                .state(resolveState(request.getStateId()))
                .city(resolveCity(request.getCityId()))
                .zone(resolveZone(request.getZoneId()))
                .pincode(request.getPincode())
                .phone(request.getPhone())
                .email(request.getEmail())
                .gstin(StringUtils.hasText(request.getGstin()) ? request.getGstin().trim().toUpperCase() : null)
                .build();

        Store saved = storeRepository.save(store);
        auditService.log(AuditAction.CREATE, "MASTER", "Store", saved.getId(), saved.getStoreCode(),
                null, null, "Store " + saved.getStoreCode() + " (" + saved.getStoreName() + ") created");
        return StoreResponse.fromEntity(saved);
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        Store store = findOrThrow(id);

        if (!store.getStoreCode().equalsIgnoreCase(request.getStoreCode())
                && storeRepository.existsByStoreCodeIgnoreCaseAndIdNot(request.getStoreCode(), id)) {
            throw new BadRequestException("A store with code '" + request.getStoreCode() + "' already exists");
        }
        if (StringUtils.hasText(request.getGstin()) && !com.storehub.util.GstinValidator.isValid(request.getGstin())) {
            throw new BadRequestException("GSTIN format is invalid");
        }

        store.setStoreCode(request.getStoreCode());
        store.setStoreName(request.getStoreName());
        store.setStoreType(request.getStoreType());
        store.setLegalName(request.getLegalName());
        store.setAddress(request.getAddress());
        store.setCountry(resolveCountry(request.getCountryId()));
        store.setState(resolveState(request.getStateId()));
        store.setCity(resolveCity(request.getCityId()));
        store.setZone(resolveZone(request.getZoneId()));
        store.setPincode(request.getPincode());
        store.setPhone(request.getPhone());
        store.setEmail(request.getEmail());
        store.setGstin(StringUtils.hasText(request.getGstin()) ? request.getGstin().trim().toUpperCase() : null);

        Store saved = storeRepository.save(store);
        auditService.log(AuditAction.UPDATE, "MASTER", "Store", saved.getId(), saved.getStoreCode(),
                null, null, "Store " + saved.getStoreCode() + " updated");
        return StoreResponse.fromEntity(saved);
    }

    @Transactional
    public StoreResponse setStatus(Long id, StoreStatus status) {
        Store store = findOrThrow(id);
        StoreStatus oldStatus = store.getStatus();
        store.setStatus(status);
        Store saved = storeRepository.save(store);
        auditService.log(AuditAction.UPDATE, "MASTER", "Store", saved.getId(), saved.getStoreCode(),
                oldStatus.name(), status.name(), "Store " + saved.getStoreCode() + " status changed to " + status);
        return StoreResponse.fromEntity(saved);
    }

    /**
     * The effective seller GST context for this store's own transactions (Multi-Store spec
     * section 32): the store's own GSTIN override when it has one, else the business-wide
     * {@link BusinessGstConfigService} singleton — never both, and never re-derived per
     * transaction, so a billing form always has one unambiguous seller anchor to compare
     * a customer/supplier's state against.
     */
    public StoreGstContextResponse resolveGstContext(Long id) {
        Store store = findOrThrow(id);
        if (StringUtils.hasText(store.getGstin())) {
            return StoreGstContextResponse.builder()
                    .storeId(store.getId())
                    .gstin(store.getGstin())
                    .legalName(store.getLegalName() != null ? store.getLegalName() : store.getStoreName())
                    .stateCode(store.getState() != null ? store.getState().getCode() : null)
                    .source("STORE")
                    .build();
        }

        BusinessGstConfigResponse business = businessGstConfigService.get();
        return StoreGstContextResponse.builder()
                .storeId(store.getId())
                .gstin(business.getGstin())
                .legalName(business.getLegalName())
                .stateCode(business.getStateCode())
                .source("BUSINESS")
                .build();
    }

    public Store findOrThrow(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Store", id));
    }

    /** The store code used for the auto-created Default Store — never reused for a real store. */
    public static final String DEFAULT_STORE_CODE = "MAIN";

    /**
     * The single anchor store that pre-Multi-Store historical data (Sales/Purchases/Inventory
     * recorded before Stores existed) is backfilled onto, and the fallback every store-aware
     * flow uses until a real store is selected (Multi-Store spec section 76 — historical
     * transaction safety). Idempotent: created once, found thereafter.
     */
    @Transactional
    public Store getOrCreateDefaultStore() {
        return storeRepository.findByStoreCodeIgnoreCase(DEFAULT_STORE_CODE)
                .orElseGet(() -> {
                    Store store = Store.builder()
                            .storeCode(DEFAULT_STORE_CODE)
                            .storeName("Main Store")
                            .storeType(StoreType.HEAD_OFFICE)
                            .status(StoreStatus.ACTIVE)
                            .build();
                    Store saved = storeRepository.save(store);
                    auditService.log(AuditAction.CREATE, "MASTER", "Store", saved.getId(), saved.getStoreCode(),
                            null, null, "Default store 'MAIN' auto-created to hold pre-Multi-Store historical data");
                    return saved;
                });
    }

    private Country resolveCountry(Long countryId) {
        return countryId != null ? countryService.findOrThrow(countryId) : null;
    }

    private State resolveState(Long stateId) {
        return stateId != null ? stateService.findOrThrow(stateId) : null;
    }

    private City resolveCity(Long cityId) {
        return cityId != null ? cityService.findOrThrow(cityId) : null;
    }

    private Zone resolveZone(Long zoneId) {
        return zoneId != null ? zoneService.findOrThrow(zoneId) : null;
    }
}
