package com.storehub.service;

import com.storehub.dto.NationalityRequest;
import com.storehub.dto.NationalityResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Country;
import com.storehub.entity.CountryStatus;
import com.storehub.entity.Nationality;
import com.storehub.entity.NationalityStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.NationalityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NationalityService {

    private final NationalityRepository nationalityRepository;
    private final CountryService countryService;

    public PagedResponse<NationalityResponse> search(String search, Long countryId, NationalityStatus status,
                                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Nationality> result = nationalityRepository.search(search, countryId, status, pageable);
        return PagedResponse.fromPage(result.map(NationalityResponse::fromEntity));
    }

    public NationalityResponse getById(Long id) {
        return NationalityResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public NationalityResponse create(NationalityRequest request) {
        if (nationalityRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A nationality named '" + request.getName() + "' already exists");
        }

        Country country = countryService.findOrThrow(request.getCountryId());
        if (country.getStatus() == CountryStatus.INACTIVE) {
            throw new BadRequestException("Country '" + country.getName() + "' is inactive and cannot be used for a new nationality");
        }

        Nationality nationality = Nationality.builder()
                .name(request.getName())
                .country(country)
                .build();

        return NationalityResponse.fromEntity(nationalityRepository.save(nationality));
    }

    @Transactional
    public NationalityResponse update(Long id, NationalityRequest request) {
        Nationality nationality = findOrThrow(id);
        Long oldCountryId = nationality.getCountry().getId();

        Country country = countryService.findOrThrow(request.getCountryId());
        if (country.getStatus() == CountryStatus.INACTIVE && !country.getId().equals(oldCountryId)) {
            throw new BadRequestException("Country '" + country.getName() + "' is inactive and cannot be used for a nationality");
        }
        if (!nationality.getName().equalsIgnoreCase(request.getName())
                && nationalityRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A nationality named '" + request.getName() + "' already exists");
        }

        nationality.setName(request.getName());
        nationality.setCountry(country);

        return NationalityResponse.fromEntity(nationalityRepository.save(nationality));
    }

    @Transactional
    public NationalityResponse setStatus(Long id, NationalityStatus status) {
        Nationality nationality = findOrThrow(id);
        nationality.setStatus(status);
        return NationalityResponse.fromEntity(nationalityRepository.save(nationality));
    }

    public Nationality findOrThrow(Long id) {
        return nationalityRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Nationality", id));
    }
}
