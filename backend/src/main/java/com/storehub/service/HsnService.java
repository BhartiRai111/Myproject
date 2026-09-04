package com.storehub.service;

import com.storehub.dto.HsnRequest;
import com.storehub.dto.HsnResponse;
import com.storehub.dto.HsnTaxRateRequest;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Hsn;
import com.storehub.entity.HsnStatus;
import com.storehub.entity.HsnTaxRate;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.HsnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HsnService {

    private final HsnRepository hsnRepository;

    public PagedResponse<HsnResponse> search(String search, HsnStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("hsnCode").ascending());
        Page<Hsn> result = hsnRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(HsnResponse::fromEntity));
    }

    public HsnResponse getById(Long id) {
        return HsnResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public HsnResponse create(HsnRequest request) {
        if (hsnRepository.existsByHsnCodeIgnoreCase(request.getHsnCode())) {
            throw new BadRequestException("An HSN code '" + request.getHsnCode() + "' already exists");
        }

        Hsn hsn = Hsn.builder()
                .hsnCode(request.getHsnCode())
                .description(request.getDescription())
                .build();
        applyTaxRates(hsn, request.getTaxRates());

        return HsnResponse.fromEntity(hsnRepository.save(hsn));
    }

    @Transactional
    public HsnResponse update(Long id, HsnRequest request) {
        Hsn hsn = findOrThrow(id);

        if (!hsn.getHsnCode().equalsIgnoreCase(request.getHsnCode())
                && hsnRepository.existsByHsnCodeIgnoreCaseAndIdNot(request.getHsnCode(), id)) {
            throw new BadRequestException("An HSN code '" + request.getHsnCode() + "' already exists");
        }

        hsn.setHsnCode(request.getHsnCode());
        hsn.setDescription(request.getDescription());
        hsn.clearTaxRates();
        applyTaxRates(hsn, request.getTaxRates());

        return HsnResponse.fromEntity(hsnRepository.save(hsn));
    }

    @Transactional
    public HsnResponse setStatus(Long id, HsnStatus status) {
        Hsn hsn = findOrThrow(id);
        hsn.setStatus(status);
        return HsnResponse.fromEntity(hsnRepository.save(hsn));
    }

    private void applyTaxRates(Hsn hsn, java.util.List<HsnTaxRateRequest> taxRateRequests) {
        if (taxRateRequests == null) {
            return;
        }
        for (HsnTaxRateRequest r : taxRateRequests) {
            HsnTaxRate rate = HsnTaxRate.builder()
                    .taxPercent(r.getTaxPercent())
                    .cgstPercent(r.getCgstPercent())
                    .sgstPercent(r.getSgstPercent())
                    .igstPercent(r.getIgstPercent())
                    .cessPercent(r.getCessPercent())
                    .effectiveFrom(r.getEffectiveFrom())
                    .build();
            hsn.addTaxRate(rate);
        }
    }

    public Hsn findOrThrow(Long id) {
        return hsnRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("HSN", id));
    }
}
