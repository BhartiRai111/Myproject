package com.storehub.service;

import com.storehub.dto.BusinessGstConfigRequest;
import com.storehub.dto.BusinessGstConfigResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.BusinessGstConfig;
import com.storehub.entity.State;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.BusinessGstConfigRepository;
import com.storehub.util.GstinValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seller's own GST registration — a singleton row (spec section 13),
 * always stored/read at id 1 rather than as a list, since one StoreHub
 * instance represents one registered business. {@link #get()} is safe to
 * call before an ADMIN has ever configured anything: it returns an empty
 * (unconfigured) row rather than 404, so read-only callers like the
 * suggest-tax-mode flow never need a special "not configured yet" branch.
 */
@Service
@RequiredArgsConstructor
public class BusinessGstConfigService {

    private static final long SINGLETON_ID = 1L;

    private final BusinessGstConfigRepository businessGstConfigRepository;
    private final StateService stateService;
    private final AuditService auditService;

    public BusinessGstConfigResponse get() {
        return BusinessGstConfigResponse.fromEntity(findOrCreate());
    }

    @Transactional
    public BusinessGstConfigResponse update(BusinessGstConfigRequest request) {
        if (request.getGstin() != null && !request.getGstin().isBlank()
                && !GstinValidator.isValid(request.getGstin())) {
            throw new BadRequestException("GSTIN '" + request.getGstin() + "' is not a valid 15-character GSTIN");
        }

        BusinessGstConfig config = findOrCreate();
        String oldGstin = config.getGstin();
        State state = request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null;

        config.setLegalName(request.getLegalName());
        config.setTradeName(blankToNull(request.getTradeName()));
        config.setGstin(blankToNull(request.getGstin()));
        config.setPan(blankToNull(request.getPan()));
        config.setAddress(blankToNull(request.getAddress()));
        config.setState(state);
        config.setPincode(blankToNull(request.getPincode()));

        BusinessGstConfig saved = businessGstConfigRepository.save(config);
        auditService.log(AuditAction.UPDATE, "GST_CONFIG", "BusinessGstConfig", saved.getId(), null,
                oldGstin, saved.getGstin(), "Business GST configuration updated");

        return BusinessGstConfigResponse.fromEntity(saved);
    }

    private BusinessGstConfig findOrCreate() {
        return businessGstConfigRepository.findById(SINGLETON_ID)
                .orElseGet(() -> businessGstConfigRepository.save(
                        BusinessGstConfig.builder().id(SINGLETON_ID).build()));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
