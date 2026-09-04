package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PartyAddressRequest;
import com.storehub.dto.PartyRequest;
import com.storehub.dto.PartyResponse;
import com.storehub.entity.*;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final CategoryService categoryService;
    private final CityService cityService;
    private final StateService stateService;
    private final CountryService countryService;

    public PagedResponse<PartyResponse> search(String search, PartyType partyType, PartyStatus status,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("partyName").ascending());
        Page<Party> result = partyRepository.search(search, partyType, status, pageable);
        return PagedResponse.fromPage(result.map(PartyResponse::fromEntity));
    }

    public PartyResponse getById(Long id) {
        return PartyResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public PartyResponse create(PartyRequest request) {
        if (partyRepository.existsByPartyCodeIgnoreCase(request.getPartyCode())) {
            throw new BadRequestException("A party with code '" + request.getPartyCode() + "' already exists");
        }
        if (StringUtils.hasText(request.getGstNumber())
                && partyRepository.existsByGstNumberIgnoreCase(request.getGstNumber())) {
            throw new BadRequestException("A party with GST number '" + request.getGstNumber() + "' already exists");
        }

        Party party = Party.builder()
                .partyCode(request.getPartyCode())
                .partyName(request.getPartyName())
                .contactPerson(request.getContactPerson())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .partyType(request.getPartyType())
                .gstNumber(request.getGstNumber())
                .panNumber(request.getPanNumber())
                .address(request.getAddress())
                .city(request.getCityId() != null ? cityService.findOrThrow(request.getCityId()) : null)
                .state(request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null)
                .country(request.getCountryId() != null ? countryService.findOrThrow(request.getCountryId()) : null)
                .pincode(request.getPincode())
                .notes(request.getNotes())
                .dealsInCategories(resolveCategories(request.getDealsInCategoryIds()))
                .build();
        applyAddresses(party, request.getAddresses());

        return PartyResponse.fromEntity(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse update(Long id, PartyRequest request) {
        Party party = findOrThrow(id);

        if (!party.getPartyCode().equalsIgnoreCase(request.getPartyCode())
                && partyRepository.existsByPartyCodeIgnoreCaseAndIdNot(request.getPartyCode(), id)) {
            throw new BadRequestException("A party with code '" + request.getPartyCode() + "' already exists");
        }
        if (StringUtils.hasText(request.getGstNumber())
                && !request.getGstNumber().equalsIgnoreCase(party.getGstNumber())
                && partyRepository.existsByGstNumberIgnoreCaseAndIdNot(request.getGstNumber(), id)) {
            throw new BadRequestException("A party with GST number '" + request.getGstNumber() + "' already exists");
        }

        party.setPartyCode(request.getPartyCode());
        party.setPartyName(request.getPartyName());
        party.setContactPerson(request.getContactPerson());
        party.setMobile(request.getMobile());
        party.setEmail(request.getEmail());
        party.setPartyType(request.getPartyType());
        party.setGstNumber(request.getGstNumber());
        party.setPanNumber(request.getPanNumber());
        party.setAddress(request.getAddress());
        party.setCity(request.getCityId() != null ? cityService.findOrThrow(request.getCityId()) : null);
        party.setState(request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null);
        party.setCountry(request.getCountryId() != null ? countryService.findOrThrow(request.getCountryId()) : null);
        party.setPincode(request.getPincode());
        party.setNotes(request.getNotes());
        party.setDealsInCategories(resolveCategories(request.getDealsInCategoryIds()));
        party.clearAddresses();
        applyAddresses(party, request.getAddresses());

        return PartyResponse.fromEntity(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse setStatus(Long id, PartyStatus status) {
        Party party = findOrThrow(id);
        party.setStatus(status);
        return PartyResponse.fromEntity(partyRepository.save(party));
    }

    private Set<Category> resolveCategories(Set<Long> categoryIds) {
        Set<Category> categories = new HashSet<>();
        if (categoryIds == null) {
            return categories;
        }
        for (Long categoryId : categoryIds) {
            categories.add(categoryService.findCategoryOrThrow(categoryId));
        }
        return categories;
    }

    private void applyAddresses(Party party, List<PartyAddressRequest> addressRequests) {
        if (addressRequests == null) {
            return;
        }
        for (PartyAddressRequest a : addressRequests) {
            PartyAddress address = PartyAddress.builder()
                    .label(a.getLabel())
                    .addressLine(a.getAddressLine())
                    .city(a.getCityId() != null ? cityService.findOrThrow(a.getCityId()) : null)
                    .state(a.getStateId() != null ? stateService.findOrThrow(a.getStateId()) : null)
                    .pincode(a.getPincode())
                    .build();
            party.addAddress(address);
        }
    }

    public Party findOrThrow(Long id) {
        return partyRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Party", id));
    }
}
