package com.storehub.service;

import com.storehub.dto.CountryRequest;
import com.storehub.dto.CountryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Country;
import com.storehub.entity.CountryStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository countryRepository;

    public PagedResponse<CountryResponse> search(String search, CountryStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Country> result = countryRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(CountryResponse::fromEntity));
    }

    public CountryResponse getById(Long id) {
        return CountryResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public CountryResponse create(CountryRequest request) {
        if (countryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A country named '" + request.getName() + "' already exists");
        }
        if (countryRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BadRequestException("A country with code '" + request.getCode() + "' already exists");
        }

        Country country = Country.builder()
                .name(request.getName())
                .code(request.getCode())
                .build();

        return CountryResponse.fromEntity(countryRepository.save(country));
    }

    @Transactional
    public CountryResponse update(Long id, CountryRequest request) {
        Country country = findOrThrow(id);

        if (!country.getName().equalsIgnoreCase(request.getName())
                && countryRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A country named '" + request.getName() + "' already exists");
        }
        if (!country.getCode().equalsIgnoreCase(request.getCode())
                && countryRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode(), id)) {
            throw new BadRequestException("A country with code '" + request.getCode() + "' already exists");
        }

        country.setName(request.getName());
        country.setCode(request.getCode());

        return CountryResponse.fromEntity(countryRepository.save(country));
    }

    @Transactional
    public CountryResponse setStatus(Long id, CountryStatus status) {
        Country country = findOrThrow(id);
        country.setStatus(status);
        return CountryResponse.fromEntity(countryRepository.save(country));
    }

    public Country findOrThrow(Long id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Country", id));
    }
}
